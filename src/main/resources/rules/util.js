"use strict";

const CLASS_CONDITION_TARGET = "condition-target";
const CLASS_PARENT_CHILD = "parent-child";
const CLASS_MISSING_RULE = "missing-rule";
const CLASS_NOT_EDGE = "not";
const CLASS_GROUP = "group";
const CLASS_HIGHLIGHTED = "highlighted";
const DIALOG_WIDTH = 1200;

let records, filteredRecords = [];

// Filters
let targetPattern = new RegExp('.*');
let correlationMin = 0;
let correlationMax = 0;
let support = 0;
let confidence = 0;
let conviction = 0;
let complexity = 10;
let convictionMax = 1.0;
let showTop = 100;

let palette = {};

// Records grouped by Condition -> Target
let groupedRecordsMap = {};

// Graph
let nodes = {};
let edges = [];

let spinner;


function initialize() {
    // Init spinner
    spinner = new Spinner({
        color: 'black', position: 'absolute',
        zIndex: 2000000000,
        left: '50%',
        top: '50%'
    }).spin();
    $('#main-panel').append(spinner.el);
    spinner.stop();

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

    // File chooser listener
    $('#file').change(function () {
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

function showProgress() {
    spinner.spin();
    $('#main-panel').append(spinner.el);
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
    showProgress();
    $('#visualize-method').removeAttr('disabled');
    $('#target-filter').removeAttr('disabled');
    $('#correlation-filter-min').removeAttr('disabled');
    $('#correlation-filter-max').removeAttr('disabled');
    $('#confidence-filter').removeAttr('disabled');
    $('#support-filter').removeAttr('disabled');
    $('#conviction-filter').removeAttr('disabled');
    $('#complexity-filter').removeAttr('disabled');
    $('#show-top-filter').removeAttr('disabled');
    ({records: records, palette: palette} = JSON.parse(content.replace("NaN", "0")));
    $.notify("Loaded " + records.length + " records", {className: "success", position: 'bottom right'});
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
    showProgress();

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
        return r2.conviction - r1.conviction;
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
    convictionMax = Math.max(...filteredRecords.map(r => r.conviction));
    if (filteredRecords.length > 0) {
        $.notify("Max conviction: " + convictionMax, {className: "info", position: 'bottom right'});
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

function RULE_GRAPH_STYLE(line_type) {
    return [
        {
            selector: "node",
            style: {
                "label": "data(label)",
                "padding-top": ".25em", "padding-bottom": ".25em",
                "padding-left": ".5em", "padding-right": ".5em",
                "font-size": 10,
                "width": "label",
                "height": "label",
                "text-valign": "center",
                "text-halign": "center",
                "shape": "roundrectangle",
                "border-width": 1,
            }
        },
        {
            selector: "node.group",
            style: {
                "text-opacity": 0,
                "background-opacity": 0,
                "border-opacity": 0.2,
            }
        },
        {
            selector: "node.colored",
            style: {
                "color": "data(text_color)",
                "background-color": "data(background_color)",
                "border-color": "black",
                "shape": "ellipse"
            }
        },
        {
            selector: "node.colored_not",
            style: {
                "color": "data(text_color)",
                "background-color": "data(background_color)",
                "border-color": "red",
            }
        },
        {
            selector: "node.highlighted",
            style: {
                "border-width": "5px",
                "border-color": "red",
            }
        },
        {
            selector: "edge",
            style: {
                "width": 1,
                "curve-style": line_type,
            }
        },
        {
            selector: "edge.highlighted",
            style: {
                'line-style': "dashed"
            }
        },
        {
            selector: "edge.not",
            style: {
                "line-color": "black",
                "width": 2
            }
        },
        {
            selector: "edge.condition-target",
            style: {
                "target-arrow-shape": "triangle-backcurve",
                "line-color": "green",
                "target-arrow-color": "green",
                "width": "data(width)"
            }
        },
        {
            selector: "edge.missing-rule",
            style: {
                "target-arrow-shape": "triangle-backcurve",
                "line-color": "gray",
                "target-arrow-color": "gray",
                "width": "data(width)",
                "opacity": 0.3
            }
        },
        {
            selector: "edge.parent-child",
            style: {
                "target-arrow-shape": "triangle-backcurve",
                "line-color": "blue",
                "target-arrow-color": "blue",
                "width": "data(width)"
            }
        }
    ];
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
    if (nodes[node.id].style.shape !== "polygon") {
        return
    }
    if (complexity !== 1) {
       $.notify('Primary effectors information is available for max complexity = 1',
           {className: "error", position: 'bottom right'});
        return
    }

    const target = node.label;
    let infoId = `aux_${node.id}`.replace(new RegExp('[@<>!\\.:;\\(\\)\\[\\] ]', 'g'), "_");
    let html = `<div id="${infoId}"></div>`;
    const panel = $('#dialog-pane');
    panel.empty();
    panel.append($(html));
    // Hack: this should saved separately per each target
    let targetAux = records.filter(el => el.target === target && el.aux && "target" in el.aux);
    if (targetAux.length === 0) {
        return
    }
    let somethingAdded = false;
    for (let e of targetAux[0].aux.target) {
        let allNamesShown =  e.names.filter(
            n => filteredRecords.filter(el => el.target === target && el.condition === n).length > 0
        ).length === e.names.filter(n => n !== target).length;
        if (allNamesShown) {
            somethingAdded = true;
            showRepresentationInfo(e, infoId);
        }
    }
    if (!somethingAdded) {
        return
    }
    let dialog = $('#dialog');
    dialog.dialog('option', 'title', `Primary effectors combinations => ${target}`);
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", DIALOG_WIDTH);
    }
    dialog.dialog('open');
}


let vennIndex = 0;

function showRepresentationInfo(representation, infoId) {
    let names = Object.entries(representation.names);
    let infoIdDiv = $(`#${infoId}`);
    infoIdDiv.append($(`<br>`));
    let probabilities = Object.entries(representation.probabilities);
    let tableHtml = `
<table class="table table-condensed table-tiny table-bordered">
    <thead class="thead-default">
        <tr>` + names.map(m => `<td>${m[1]}</td>`).join("") + `<td>p</td></tr>
    </thead>
    <tbody>
        </tr>`;
    for (let v = 0; v < probabilities.length; v++) {
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
        tableHtml += `<td style="background-color: rgba(0,0,255, ${probabilities[v][1]}">${probabilities[v][1]}</td>`;
        tableHtml += `</tr>`;
    }
    tableHtml += `
    </tbody>
</table>`;
    if (names.length <= 5) {
        let vennData = [];
        // Compute Venn numbers out of probabilities
        for (let v = 1; v < (1 << names.length); v++) {
            let sets = [];
            let size = 0;
            for (let i = 0; i < names.length; i++) {
                if ((v & (1 << i)) !== 0) {
                    sets.push(names[i][1]);
                }
            }
            for (let x = 0; x < probabilities.length; x++) {
                if ((v & x) === v) {
                    size += probabilities[x][1];
                }
            }
            if (size > 0) {
                vennData.push({'sets': sets, 'size': size});
            }
        }
        vennIndex += 1;
        infoIdDiv.append($(`<ul><li>${tableHtml}</li><li><div id="venn${vennIndex}" class="venn"></div></li></ul>`));
        let div = d3.select(`#venn${vennIndex}`);
        div.datum(vennData).call(venn.VennDiagram());

        // add a tooltip
        let tooltip = $(`<div></div>`);
        infoIdDiv.append(tooltip);

        // add listeners to all the groups to display tooltip on mouseover
        div.selectAll("g")
            .on("mouseover", function (d, i) {
                // sort all the areas relative to the current item
                venn.sortAreas(div, d);

                // Display a tooltip
                tooltip.text(d.sets + " : " + d.size);

                // highlight the current path
                var selection = d3.select(this);
                selection.select("path")
                    .style("stroke-width", 3)
                    .style("fill-opacity", d.sets.length == 1 ? .4 : .1)
                    .style("stroke-opacity", 1);
            })
            .on("mouseout", function (d, i) {
                // Display a tooltip with the current size
                tooltip.text("");
                var selection = d3.select(this);
                selection.select("path")
                    .style("stroke-width", 0)
                    .style("fill-opacity", d.sets.length == 1 ? .25 : .0)
                    .style("stroke-opacity", 0);
            });
    }

    else {
        infoIdDiv.append($(tableHtml));
    }
}

function toggleAuxInfo(e, infoId) {
    if (e.value === '+') {
        let r = recordsAuxMap[infoId];
        showRepresentationInfo(r.aux.rule, infoId);
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
            <th>id</th><th>condition</th><th>target</th><th>#c</th><th>#t</th><th>#\u2229</th><th>#d</th><th>corr</th><th>supp</th><th>conf</th><th>conv</th><th></th>
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
    <td>${r.conviction.toFixed(2)}</td>
    <td>
        <input type="button" onclick="toggleAuxInfo(this, '${infoId}');" value="+"/>
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