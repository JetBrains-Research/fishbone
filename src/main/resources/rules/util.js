"use strict";

const CLASS_CONDITION_TARGET = "condition-target";
const CLASS_AND = "and";
const CLASS_PARENT_CHILD = "parent-child";
const CLASS_OR = "or";
const CLASS_MISSING_RULE = "missing-rule";
const CLASS_NOT_EDGE = "not";
const CLASS_GROUP = "group";
const CLASS_HIGHLIGHTED = "highlighted";
const DIALOG_WIDTH = 1200;

let fishboneResponse = null, ripperResponse = null;
let records, filteredRecords = [];

// Filters
let targetPattern = new RegExp('.*');
let correlationMin = 0;
let correlationMax = 0;
let support = 0;
let confidence = 0;
let conviction = 0;
let complexity = 10;
let criterionMax = 1.0;
let showTop = 100;

let palette = {};
let criterion = "conviction";

// Records grouped by Condition -> Target
let groupedRecordsMap = {};

let spinner;


function initialize() {
    // Init spinner
    spinner = new Spinner({
        color: 'black', position: 'absolute',
        zIndex: 2000000000,
        left: '50%',
        top: '50%'
    });
    $('#main-panel').append(spinner.el);

    // Bind render on change
    $('#target-filter').change(filterAndRender);
    $('#correlation-filter-min').change(filterAndRender);
    $('#correlation-filter-max').change(filterAndRender);
    $('#support-filter').change(filterAndRender);
    $('#confidence-filter').change(filterAndRender);
    $('#conviction-filter').change(filterAndRender);
    $('#complexity-filter').change(filterAndRender);
    $('#show-top-filter').change(filterAndRender);
    $('#visualize-method').change(filterAndRender);

    $('#experiment-type').change(function () {
        if ($(this).val() === 'GENOME') {
            $("#genome-label").show()
            $("#genome").show()
        }
        if ($(this).val() === 'FEATURE_SET') {
            $("#genome-label").hide()
            $("#genome").hide()
        }
    });

    // File chooser listener
    $('#file').change(function () {
        $('#decisiontree-alg-dialog-pane').empty();
        $('#fpgrowth-alg-dialog-pane').empty();

        const [file] = this.files;
        $('#filename').text(file.name);
        const reader = new FileReader();
        reader.onload = (event) => {
            try {
                let content = event.target.result;
                load(content);
            } catch (err) {
                spinner.stop();
                $.notify(err, {className: "error", position: 'bottom right'});
                throw err;
            }
        };
        reader.readAsText(file);
    });
    $('#tree-file').change(function () {
        $('#decisiontree-alg-dialog-pane').empty();
        $('#fpgrowth-alg-dialog-pane').empty();

        const [file] = this.files;
        $('#tree-filename').text(file.name);
        const reader = new FileReader();
        reader.onload = (event) => {
            try {
                let content = event.target.result;
                showDecisionTree(content);
            } catch (err) {
                spinner.stop();
                $.notify(err, {className: "error", position: 'bottom right'});
                throw err;
            }
        };
        reader.readAsText(file);
    });
    $('#fpgrowth').change(function () {
        $('#decisiontree-alg-dialog-pane').empty();
        $('#fpgrowth-alg-dialog-pane').empty();

        const [file] = this.files;
        $('#tree-filename').text(file.name);
        const reader = new FileReader();
        reader.onload = (event) => {
            try {
                let content = event.target.result;
                showFPGrowthTable(content);
            } catch (err) {
                spinner.stop();
                $.notify(err, {className: "error", position: 'bottom right'});
                throw err;
            }
        };
        reader.readAsText(file);
    });

    window.myForm = new FormData();
    $('#predicates-file').change(function () {
        window.myForm.delete('predicates');
        for (var i = 0; i < this.files.length; ++i) {
            window.myForm.append('predicates', this.files[i]);
        }
        $.notify("Uploaded " + this.files.length + " predicates", {className: "success", position: 'bottom right'});
    });
    $('#bed-database-file').change(function () {
        window.myForm.delete('database');
        window.myForm.append('database', this.files[0]);
        $.notify("Uploaded database", {className: "success", position: 'bottom right'});
    });
    $('#targets-file').change(function () {
        window.myForm.delete('targets');
        for (var i = 0; i < this.files.length; ++i) {
            window.myForm.append('targets', this.files[i]);
        }
        $.notify("Uploaded " + this.files.length + " targets", {className: "success", position: 'bottom right'});
    });

    // Load by hash if possible, this is useful for pipeline
    const {hash} = window.location;
    if (hash.length) {
        $.get(hash.substr(1), data => {
            try {
                load(data);
            } catch (err) {
                spinner.stop();
                $.notify(err, {className: "error", position: 'bottom right'});
                throw err;
            }
        }, "text");
    }
}

function showFPGrowthTable(table) {
    let tableHtml = `
                            <table class="table table-condensed table-tiny table-bordered" id="fp-growth-table">
                                <thead class="thead-default"></thead>
                                <tbody>
                                     <tr>
                                        <th>Rule</th>
                                        <th>Support</th>
                                        <th>Confidence</th>
                                     </tr>
                                    </tr>`;
    for (let v = 0; v < table.split("\n").length - 1; v++) {
        let line = table.split("\n")[v];
        let rule = line.match(/\[.*?\] ==> \[.*?\]/g)[0];
        let support = line.match(/support = [0-9]*\.?[0-9]+/g)[0].split(" = ")[1];
        let confidence = line.match(/confidence = [0-9]*\.?[0-9]+/g)[0].split(" = ")[1];
        tableHtml += `<tr>`;
        tableHtml += (`<td>` + rule + `</td>`);
        tableHtml += (`<td>` + support + `</td>`);
        tableHtml += (`<td>` + confidence + `</td>`);
        tableHtml += `</tr>`;
    }
    tableHtml += `
                                </tbody>
                            </table>`;

    const panel = $('#fpgrowth-alg-dialog-pane');
    panel.empty();
    let dialog = $('#fpgrowth-alg-dialog');
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", DIALOG_WIDTH);
    }
    panel.append($(tableHtml));
    dialog.dialog('open');
}

function renderFpGrowthAlgorithmResults(res) {
    document.getElementById('filename-to-download').value = res["FP_GROWTH"];
    let host = window.location.protocol + "//" + window.location.host;
    $.ajax({
        url: `${host}/rules`,
        type: "GET",
        data: {filename: res["FP_GROWTH"]},
        success: function (res2) {
            showFPGrowthTable(res2);
        },
        error: function (error) {
            console.log(error);
        }
    });
}

function showDecisionTree(tree) {
    const panel = $('#decisiontree-alg-dialog-pane');
    panel.empty();
    let dialog = $('#decisiontree-alg-dialog');
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", DIALOG_WIDTH);
    }

    var viz = new Viz();

    viz.renderSVGElement(tree, {'engine': 'dot'})
        .then(function (element) {
            panel.append($(element));
        })
        .catch(error => {
            viz = new Viz();
            console.error(error);
        });
    dialog.dialog('open');
}

function renderDecisionTreeAlgorithmsResults(res) {
    document.getElementById('filename-to-download').value = res["DECISION_TREE"];
    let host = window.location.protocol + "//" + window.location.host;
    $.ajax({
        url: `${host}/rules`,
        type: "GET",
        data: {filename: res["DECISION_TREE"]},
        success: function (res3) {
            showDecisionTree(res3);
        },
        error: function (error) {
            console.log(error);
        }
    })
}

function renderFishboneResults(jsonPath) {
    let host = window.location.protocol + "//" + window.location.host;
    $.ajax({
        url: `${host}/rules`,
        type: "GET",
        data: {filename: jsonPath},
        success: function (res) {
            load(JSON.stringify(res));

        },
        error: function (error) {
            console.log(error);
        }
    })
}

function getMiners() {
    var miners = "fishbone";
    if (document.getElementById("ripperAlgCheckbox").checked) {
        miners += ", ripper";
    }
    if (document.getElementById("fpGrowthAlgCheckbox").checked) {
        miners += ", fp-growth";
    }
    if (document.getElementById("decisionTreeAlgCheckbox").checked) {
        miners += ", tree";
    }
    return miners;
}

function runAnalysisOnLoadedData() {
    console.log("Sending request");
    $('#decisiontree-alg-dialog-pane').empty();
    $('#fpgrowth-alg-dialog-pane').empty();

    window.myForm.append("experiment", document.getElementById('experiment-type').value.toUpperCase());
    window.myForm.append("genome", document.getElementById('genome').value.toLowerCase());
    window.myForm.append("runName", document.getElementById('run-name').value);
    window.myForm.append("significanceLevel", document.getElementById('significance-level').value);
    window.myForm.append("criterion", document.getElementById("info-criterion").value);
    window.myForm.append("topRules", document.getElementById("topRules").value);
    window.myForm.append("exploratoryFraction", document.getElementById("exploratoryFraction").value);
    window.myForm.append("nSampling", document.getElementById("nSampling").value);
    window.myForm.append("samplingStrategy", document.getElementById("samplingStrategy").value);
    window.myForm.append("alphaHoldout", document.getElementById("alphaHoldout").value);
    window.myForm.append("alphaFull", document.getElementById("alphaFull").value);
    var miners = getMiners();
    if (miners === "") {
        $.notify('No one algorithm was selected', {className: "error", position: 'bottom right'});
        return
    }
    window.myForm.append("miners", miners);

    spinner.spin();
    let host = window.location.protocol + "//" + window.location.host;
    $.ajax({
        url: `${host}/rules`,
        type: "POST",
        data: window.myForm,
        processData: false,
        contentType: false,
        success: function (response) {
            if (response["FISHBONE"] != null) {
                $('#fishboneSwitch').val("Switch to Ripper");

                fishboneResponse = response["FISHBONE"];
                document.getElementById('filename-to-download').value = fishboneResponse;
                renderFishboneResults(fishboneResponse);
            }
            if (response["FP_GROWTH"] != null) {
                renderFpGrowthAlgorithmResults(response);
            }
            if (response["DECISION_TREE"] != null) {
                renderDecisionTreeAlgorithmsResults(response);
            }
            if (response["RIPPER"] != null) {
                ripperResponse = response["RIPPER"];
            }
            spinner.stop();
        },
        error: function (errResponse) {
            console.log(errResponse);
        }
    });
}

function downloadFile() {
    let jsonPath = document.getElementById('filename-to-download').value;
    let experiment = document.getElementById('experiment-type-download').value.toUpperCase();
    let host = window.location.protocol + "//" + window.location.host;
    $.ajax({
        url: `${host}/rules`,
        type: "GET",
        data: {filename: jsonPath, experiment: experiment},
        success: function (res) {
            let nameParts = jsonPath.split(".");
            let type = nameParts.pop();
            let name = nameParts.pop();
            downloadTextFile(JSON.stringify(res), name + "." + type);
        },
        error: function (error) {
            console.log(error);
        }
    })
}

// TODO: check
function downloadTextFile(text, name) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([text]));
    a.download = name;
    a.click();
}

function showFPGrowthResults() {
    let dialog = $('#fpgrowth-alg-dialog');
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", DIALOG_WIDTH);
    }

    var checkBox = document.getElementById("fpGrowthCheckbox");
    if (checkBox.checked === true) {
        dialog.dialog('open');
    } else {
        dialog.dialog('close');
    }
}

function showDecisionTreeResults() {
    let dialog = $('#decisiontree-alg-dialog');
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", DIALOG_WIDTH);
    }

    var checkBox = document.getElementById("decisionTreeCheckbox");
    if (checkBox.checked === true) {
        dialog.dialog('open');
    } else {
        dialog.dialog('close');
    }
}

function switchFishboneResults() {
    let switchButton = $('#fishboneSwitch');
    if (ripperResponse != null && switchButton.val() === "Switch to Ripper") {
        switchButton.val("Switch to Fishbone");
        renderFishboneResults(ripperResponse);
    } else if (fishboneResponse != null && switchButton.val() === "Switch to Fishbone") {
        switchButton.val("Switch to Ripper");
        renderFishboneResults(fishboneResponse);
    }
}

/**
 * Group records by condition -> target
 */
function groupRecordsByConditionTarget() {
    groupedRecordsMap = {};
    for (let r of records) {
        const id = edgeId(r.condition, r.target);
        if (!(id in groupedRecordsMap)) {
            groupedRecordsMap[id] = [];
        }
        groupedRecordsMap[id].push(r);
    }
}


function load(content) {
    spinner.spin();
    $('#visualize-method').removeAttr('disabled');
    $('#target-filter').removeAttr('disabled');
    $('#correlation-filter-min').removeAttr('disabled');
    $('#correlation-filter-max').removeAttr('disabled');
    $('#confidence-filter').removeAttr('disabled');
    $('#support-filter').removeAttr('disabled');
    $('#conviction-filter').removeAttr('disabled');
    $('#complexity-filter').removeAttr('disabled');
    $('#show-top-filter').removeAttr('disabled');
    ({records: records, palette: palette, criterion: criterion} = JSON.parse(content.replace("NaN", "0")));
    $.notify("Loaded " + records.length + " records. Fishbone used " + criterion + " criterion", {
        className: "success",
        position: 'bottom right'
    });
    groupRecordsByConditionTarget();
    filterAndRender();
}

function filter_record(r) {
    return targetPattern.test(r.target) &&
        r.correlation >= correlationMin &&
        r.correlation <= correlationMax &&
        r.complexity <= complexity &&
        r.support >= support &&
        r.confidence >= confidence &&
        r.conviction >= conviction;
}

/*
 * Apply filters to loaded rules records
 */
function filterAndRender() {
    spinner.spin();

    const target = $('#target-filter').val().trim();
    if (target.length > 0) {
        targetPattern = new RegExp(target);
    } else {
        targetPattern = new RegExp('.*');
    }

    let correlationFieldMin = $('#correlation-filter-min').val().trim();
    if (correlationFieldMin.length > 0) {
        correlationMin = parseFloat(correlationFieldMin);
    }
    let correlationFieldMax = $('#correlation-filter-max').val().trim();
    if (correlationFieldMax.length > 0) {
        correlationMax = parseFloat(correlationFieldMax);
    }
    let supportField = $('#support-filter').val().trim();
    if (supportField.length > 0) {
        support = parseFloat(supportField);
    }
    let confidenceField = $('#confidence-filter').val().trim();
    if (confidenceField.length > 0) {
        confidence = parseFloat(confidenceField);
    }

    let convictionField = $('#conviction-filter').val().trim();
    if (convictionField.length > 0) {
        conviction = parseFloat(convictionField);
    }
    let complexityField = $('#complexity-filter').val().trim();
    if (complexityField.length > 0) {
        complexity = parseInt(complexityField);
    }
    let showTopField = $('#show-top-filter').val().trim();
    if (showTopField.length > 0) {
        showTop = parseInt(showTopField);
    }

    filteredRecords = records.filter(el => filter_record(el));
    // Sort records to show top
    filteredRecords.sort(function (r1, r2) {
        return r2[criterion] - r1[criterion];
    });
    console.info("Filtered records: " + filteredRecords.length);
    if (filteredRecords.length > showTop) {
        console.info("Show top : " + showTop);
        $.notify("Displayed top " + showTop + " out of " + filteredRecords.length, {
            className: "warn",
            position: 'bottom right'
        });
        filteredRecords = filteredRecords.slice(0, showTop);
    } else {
        $.notify("Displayed " + filteredRecords.length, {className: "info", position: 'bottom right'});
    }
    criterionMax = Math.max(...filteredRecords.map(r => r[criterion]));
    if (filteredRecords.length > 0) {
        $.notify("Max " + criterion + ":" + criterionMax, {className: "info", position: 'bottom right'});
    }
    if ($('#visualize-method').val() === "Fishbone") {
        renderFishBone();
    } else {
        renderGraph();
    }
}


/* Unified ID */
function edgeId(start, end) {
    return start + ":" + end;
}

/**
 * See https://stackoverflow.com/questions/3942878/how-to-decide-font-color-in-white-or-black-depending-on-background-color
 */
function textColor(background) {
    const color = (background.charAt(0) === '#') ? background.substring(1, 7) : background;
    const r = parseInt(color.substring(0, 2), 16); // hexToR
    const g = parseInt(color.substring(2, 4), 16); // hexToG
    const b = parseInt(color.substring(4, 6), 16); // hexToB
    return (((r * 0.299) + (g * 0.587) + (b * 0.114)) > 186) ? "black" : "white";
}

/**
 * Generate target information.
 * This is a fast hack to investigate before the paper is out, must be refactored.
 */
function showInfoNode(node) {
    console.info("Information node: " + node.id);
    // Hack check for fish head
    if (!node.hasOwnProperty(CLASS_FISHBONE_HEAD)) {
        return
    }
    const target = node.label;
    let infoId = `aux_${node.id}`.replace(new RegExp('[@<>!\\.:;\\(\\)\\[\\] ]', 'g'), "_");
    let html = `<div id="${infoId}"></div>`;
    const panel = $('#dialog-pane');
    panel.empty();
    panel.append($(html));
    // Hack[shpynov]: we save single technical record TRUE => target with this information
    let targetAux = records.filter(el =>
        el.target === target && el.aux && ("upset" in el.aux || "correlations" in el.aux));
    if (targetAux.length === 0) {
        return
    }
    showInfoTarget(targetAux[0].aux, infoId);
    let dialog = $('#dialog');
    dialog.dialog('option', 'title', `? => ${target}`);
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", DIALOG_WIDTH);
    }
    dialog.dialog('open');
}

let vennIndex = 0;

function showInfoRule(combination, infoId) {
    let names = Object.entries(combination.names);
    let infoIdDiv = $(`#${infoId}`);
    infoIdDiv.append($(`<br>`));
    let combinations = Object.entries(combination.combinations);
    let tableHtml = `
<table class="table table-condensed table-bordered table-striped vertical-centered">
    <thead class="thead-default">
        <tr>` + names.map(m => `<td>${m[1]}</td>`).join("") + `<td>#</td></tr>
    </thead>
    <tbody>
        </tr>`;
    let total = 0;
    for (let v = 1; v < combinations.length; v++) {
        total += combinations[v][1];
    }
    // Ignore everything false
    for (let v = 1; v < combinations.length; v++) {
        tableHtml += `<tr>`;
        for (let i = 0; i < names.length; i++) {
            tableHtml += `<td>`;
            if ((v & (1 << i)) !== 0) {
                tableHtml += 'T'
            } else {
                tableHtml += 'F';
            }
            tableHtml += `</td>`;
        }
        let size = combinations[v][1];
        tableHtml += `<td style="background-color: rgba(0,0,255, ${size / total}">${size}</td>`;
        tableHtml += `</tr>`;
    }
    tableHtml += `
    </tbody>
</table>`;
    // https://github.com/benfred/venn.js/
    if (names.length <= 5) {
        let vennData = [];
        // Compute Venn numbers out of combinations
        for (let v = 1; v < (1 << names.length); v++) {
            let sets = [];
            let size = 0;
            for (let i = 0; i < names.length; i++) {
                if ((v & (1 << i)) !== 0) {
                    sets.push(names[i][1]);
                }
            }
            for (let x = 0; x < combinations.length; x++) {
                if ((v & x) === v) {
                    size += combinations[x][1];
                }
            }
            if (size > 0) {
                vennData.push({'sets': sets, 'size': size});
            }
        }

        vennIndex += 1;
        infoIdDiv.append($(`<ul><li>${tableHtml}</li><li><div id="venn${vennIndex}"></div></li></ul>`));
        let div = d3.select(`#venn${vennIndex}`);
        div.datum(vennData).call(venn.VennDiagram()
            .width(500)
            .height(300)
            .fontSize("13px"));

        const vennDiv = $(`#venn${vennIndex}`);
        const tooltipDx = vennDiv.position().left + 10;
        const tooltipDy = vennDiv.position().top + 10;
        var tooltip = d3.select(`#venn${vennIndex}`).append("div")
            .attr("class", "tooltip");

        div.selectAll("path")
            .style("stroke-opacity", 0)
            .style("stroke", "#000000")
            .style("stroke-width", 3);

        div.selectAll("g")
            .on("mouseover", function (d, i) {
                // sort all the areas relative to the current item
                venn.sortAreas(div, d);

                // Display a tooltip with the current size
                tooltip.style("opacity", .9);
                tooltip.text(d.size);

                // highlight the current path
                var selection = d3.select(this);
                selection.select("path")
                    .style("fill-opacity", d.sets.length === 1 ? .4 : .1)
                    .style("stroke-opacity", 1);
            })

            .on("mousemove", function () {
                tooltip.style("left", (d3.event.offsetX + tooltipDx) + "px")
                    .style("top", (d3.event.offsetY + tooltipDy) + "px");
            })

            .on("mouseout", function (d, i) {
                tooltip.style("opacity", 0);
                var selection = d3.select(this);
                selection.select("path")
                    .style("fill-opacity", d.sets.length === 1 ? .25 : .0)
                    .style("stroke-opacity", 0);
            });
    } else {
        infoIdDiv.append($(tableHtml));
    }
}

let upsetIndex = 0;

function showUpset(upset, infoId) {
    let infoIdDiv = $(`#${infoId}`);
    infoIdDiv.append($(`<br>`));
    upsetIndex += 1;
    infoIdDiv.append($(`<div id="upset${upsetIndex}" class="venn"></div>`));
    let data = [];
    for (let d of Object.entries(upset.data)) {
        data.push(d[1]);
    }

    visualizeUpset("upset" + upsetIndex, upset.names, data, 6);
}


let heatmapIndex = 0;

function showHeatmap(heatmap, infoId) {
    let infoIdDiv = $(`#${infoId}`);
    infoIdDiv.append($(`<br>`));
    heatmapIndex += 1;
    infoIdDiv.append($(`<div id="heatmap${heatmapIndex}"></div>`));
    drawHeatMap("heatmap" + heatmapIndex, heatmap.tableData, heatmap.rootData, heatmap.rootData,
        Math.max(10, 200 / heatmap.tableData.length));
}

function showInfoTarget(aux, infoId) {
    let infoIdDiv = $(`#${infoId}`);
    if (aux.heatmap != null) {
        const heatmapId = infoId + "_heatmap";
        infoIdDiv.append($(`
<div class="panel panel-default">
  <div class="panel-heading">
    <h2 class="panel-title">Correlation</h2>
  </div>
  <div id=${heatmapId} class="panel-body">
  </div>
</div>`));
        showHeatmap(aux.heatmap, heatmapId);
    }
    if (aux.upset != null) {
        const upsetId = infoId + "_upset";
        infoIdDiv.append($(`
<div class="panel panel-default">
  <div class="panel-heading">
    <h2 class="panel-title">Combinations overlap</h2>
  </div>
  <div id=${upsetId} class="panel-body" style="overflow-x: scroll;">
  </div>
</div>`));
        showUpset(aux.upset, upsetId);
    }
}

function toggleAux(e, infoId) {
    if (e.value === '+') {
        let r = recordsAuxMap[infoId];
        showInfoRule(r.aux.rule, infoId);
        e.value = '-'
    } else {
        $(`#${infoId}`).empty();
        e.value = '+'
    }
}

const recordsAuxMap = {};

/**
 * Generate rules statistics information and display it as modal dialog
 */
function showInfoEdge(edge) {
    console.info("Information edge: " + edge.id);
    if (!edge.records) {
        return
    }
    highlightEdge(edge);
    const dialog = $('#dialog');
    const panel = $('#dialog-pane');
    let html = "";
    if (dialog.dialog('isOpen') === true) {
        html = panel.html().replace(new RegExp('</tbody></table>$', 'g'), '');
    } else {
        html = `
<table class="table table-condensed">
    <thead class="thead-default">
        <tr>
            <th>id</th><th>condition</th><th>target</th><th>#c</th><th>#t</th><th>#\u2229</th><th>#d</th><th>corr</th><th>supp</th><th>conf</th><th>${criterion}</th><th></th>
        </tr>
    </thead>
    <tbody>
`
    }

    const processed = new Set();
    for (let r of edge.records) {
        const id = edgeId(r.condition, r.target);
        if (processed.has(id)) {
            return
        } else {
            processed.add(id)
        }
        console.info("Rule: " + r.condition + "=>" + r.target);
        if (id in groupedRecordsMap) {
            let infoId = `aux_${r.id}_${id}`.replace(new RegExp('[@<>!\\.:;\\(\\)\\[\\] ]', 'g'), "_");
            recordsAuxMap[infoId] = r;
            // Build html table
            html += groupedRecordsMap[id].map(r => `
<tr>
    <td>${r.id}</td>
    <td>${r.condition}</td>
    <td>${r.target}</td>
    <td>${r.condition_count}</td>
    <td>${r.target_count}</td>
    <td>${r.intersection_count}</td>
    <td>${r.database_count}</td>
    <td>${r.correlation.toFixed(2)}</td>
    <td>${r.support.toFixed(2)}</td>
    <td>${r.confidence.toFixed(2)}</td>
    <td>${r[criterion].toFixed(2)}</td>
    <td>
        <input type="button" onclick="toggleAux(this, '${infoId}');" value="+"/>
    </td>
</tr>
<tr>
    <td colspan="12">
        <div id="${infoId}"></div>
    </td>
</tr>
`).join("");
        }
    }

    html += `</tbody></table>`;
    panel.empty();
    panel.append($(html));
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", DIALOG_WIDTH);
    }
    dialog.dialog("option", "width", "Rules information");
    dialog.dialog('open');
}


function highlightEdge(edge) {
    // Remove highlighting
    window.cy.nodes().removeClass('highlighted');
    window.cy.edges().removeClass('highlighted');
    console.info("Highlighting edge: " + edge.id);

    function traverse(r, target, visited, f) {
        if (visited.has(r.condition)) {
            return
        }
        visited.add(r.condition);
        console.info("TRAVERSE: " + r.condition + "=>" + target);
        if (r.hasOwnProperty("parent_node")) {
            // Show target link in case we don't have any direct path
            f(r.node, r.parent_node);
            for (let parent of records.filter(el => el.target === target && el.condition === r.parent_condition)) {
                traverse(parent, target, visited, f);
            }
        } else {
            f(r.node, target);
        }
    }

    const processed = new Set();
    for (let r of edge.records) {
        const id = edgeId(r.condition, r.target);
        if (processed.has(id)) {
            return
        } else {
            processed.add(id);
            const visited = new Set();
            traverse(r, r.target, visited, function (c, t) {
                console.info('Traversing edge:' + edgeId(c, t));
                window.cy.getElementById(edgeId(c, t)).addClass(CLASS_HIGHLIGHTED);
                window.cy.getElementById(c).addClass(CLASS_HIGHLIGHTED);
                window.cy.getElementById(t).addClass(CLASS_HIGHLIGHTED);
            });
        }
    }
}