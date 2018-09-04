"use strict";

const CLASS_CONDITION_TARGET = "condition-target";
const CLASS_PARENT_CHILD = "parent-child";
const CLASS_MISSING_RULE = "missing-rule";
const CLASS_NOT_EDGE = "not";
const CLASS_GROUP = "group";
const CLASS_HIGHLIGHTED = "highlighted";

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

function createAuxInfo(aux, target) {
    if ("marginals" in aux) {
        let ms = Object.entries(aux.marginals);
        let ps = Object.entries(aux.probabilities);
        return `
<table class="table table-condensed table-tiny table-bordered">
    <thead class="thead-default">
        <tr>` + ms.map(m => `<td>${m[0]}</td>`).join("") + `<td>p</td></tr>
    </thead>
    <tbody>
        <tr>` + ms.map(m => `<td>${m[1]}</td>`).join("") + `<td>1.0</td>
        </tr>` +
            ps.map(p => `
        <tr>${p[0].replace(new RegExp('1', 'g'), '<td>T</td>').replace(new RegExp('0', 'g'), '<td>F</td>')}
            <td style="background-color: rgba(0,0,255, ${p[1]}">${p[1]}</td>
        </tr>`).join("") +
    `</tbody>
</table>`;
    }
    return Object.entries(aux)
        .filter(e => Object.keys(edges).filter(el => el.includes(edgeId(e[0], target))).length > 0)
        .map(e => createAuxInfo(e[1], target)).join("");

}

function toggleCollapseButton(e) {
    if (e.value === '+') {
        e.value = '-'
    } else {
        e.value = '+'
    }
}

/**
 * Generate rules statistics information and display it as modal dialog
 */
function showInfo(edge) {
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
            let infoId = `aux_${r.id}_${id}`.replace(new RegExp('[\\.:;\\(\\)\\[\\] ]', 'g'), "_");
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
        <input type="button" data-toggle="collapse" data-target=#${infoId} onclick="toggleCollapseButton(this);" value="+"/>
    </td>
</tr>
<tr>
    <td colspan="12">
        <div id=${infoId} class="collapse" >
    ${createAuxInfo(r.aux, r.target)} 
        </div>
    </td>
</tr>
`).join("");
        }
    }

    html += `</tbody></table>`;
    panel.empty();
    panel.append($(html));
    if (dialog.dialog('isOpen') !== true) {
        dialog.dialog("option", "width", 1000);
    }
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