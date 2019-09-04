"use strict";

function renderGraph() {
    spinner.spin();
    // Build graph
    const ne = buildGraph();

    const nodes = ne[0];
    const edges = ne[1];

    const container = $('#cy');
    container.empty();

    // Render
    const elements = [].concat(Object.values(nodes), Object.values(edges));
    if (window.hasOwnProperty("cy")) {
        window.cy.removeData();
    }
    const cy = window.cy = cytoscape({
        container: container,
        elements: elements,
        layout: {
            name: 'cose-bilkent',
            animate: false,
            randomize: true
        },
        style: GRAPH_STYLE,
        minZoom: 0.1,
        maxZoom: 10,
    });
    cy.on('tap', 'edge', function (evt) {
        showInfoEdge(evt.target._private.data);
    });
    cy.on('tap', 'node', function (evt) {
        showInfoNode(evt.target._private.data);
    });
    spinner.stop();
}

function buildGraph() {
    const nodes = {};
    const edges = {};

    function addNode(n) {
        console.info("NODE: " + n);
        let not_n = n.replace("NOT ", "");
        // Group nodes
        let n_group_id = not_n + "_group";
        nodes[n_group_id] = {
            group: 'nodes',
            data: {
                id: n_group_id,
                label: ''
            },
            classes: CLASS_GROUP
        };
        if (!(n in nodes)) {
            if (not_n in palette) {
                let classes = "colored";
                if (n !== not_n) {
                    classes = "colored_not";
                }
                nodes[n] = {
                    data: {
                        id: n,
                        label: n,
                        text_color: textColor(palette[not_n]),
                        background_color: palette[not_n],
                        parent: n_group_id
                    },
                    group: 'nodes',
                    classes: classes
                };
            } else {
                nodes[n] = {data: {id: n, label: n}};
            }
        }
        if (not_n !== n) {
            addNode(not_n);
            let not_edge_id = edgeId(n, not_n);
            edges[not_edge_id] = {
                group: 'nodes',
                data: {
                    id: not_edge_id,
                    label: "",
                    source: not_n,
                    target: n,
                    records: []
                },
                classes: CLASS_NOT_EDGE
            };
            nodes[not_n]['data']['parent'] = n_group_id;
        }
    }

    function addEdge(start, end, record, classes) {
        let width = Math.round(1 + 5 * record[criterion] / criterionMax);
        const id = edgeId(start, end);
        console.info("EDGE: " + id + " [" + classes + " " + width + "] " + record.condition + "=>" + record.target);
        if (id in edges) {
            // Update records
            const records = edges[id].data.records;
            records.push(record);
            // Update classes
            if (classes !== CLASS_CONDITION_TARGET) {
                classes = edges[id].classes
            }
            // Update width
            width = Math.max(width, edges[id].data.width);
            edges[id] = {
                group: 'edges',
                data: {
                    id: id,
                    source: start,
                    target: end,
                    records: records,
                    width: width,
                },
                classes: classes,
            };
        } else {
            edges[id] = {
                group: 'edges',
                data: {
                    id: id,
                    source: start,
                    target: end,
                    records: [record],
                    width: width,
                },
                classes: classes,
            };
        }
    }

    function process(r, target, processed) {
        // Ignore already processed
        if (processed.has(r.condition)) {
            return
        }
        processed.add(r.condition);
        console.info("PROCESS: " + r.condition + "=>" + r.target);
        if (r.hasOwnProperty("parent_node")) {
            // Show target link in case we don't have any direct path
            addNode(r.node);
            addNode(r.parent_node);
            let classes = CLASS_MISSING_RULE;
            if (filter_record(r)) {
                classes = CLASS_PARENT_CHILD;
            }
            addEdge(r.node, r.parent_node, r, classes);
            for (let parent of records.filter(el => el.target === target && el.condition === r.parent_condition)) {
                process(parent, target, processed);
            }
        } else {
            addNode(r.node);
            addNode(target);
            let classes;
            if (filter_record(r)) {
                classes = CLASS_CONDITION_TARGET;
            } else {
                classes = CLASS_MISSING_RULE;
            }
            addEdge(r.node, target, r, classes);
        }
    }

    // Process per target
    for (let target of new Set(filteredRecords.map(el => el.target))) {
        const processed = new Set();
        for (let r of filteredRecords.filter(el => el.target === target)) {
            process(r, target, processed);
        }
    }
    return [nodes, edges];
}

const GRAPH_STYLE = [
    {
        selector: "node",
        style: {
            "label": "data(label)",
            "padding-top": ".25em", "padding-bottom": ".25em",
            "padding-left": ".5em", "padding-right": ".5em",
            "font-size": 11,
            "width": "label",
            "height": "label",
            "text-valign": "center",
            "text-halign": "center",
            "border-width": 1,
        }
    },
    {
        selector: "node.group",
        style: {
            "text-opacity": 0,
            "background-opacity": 0.1,
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
            "border-width": "2px",
            "border-color": "red",
        }
    },
    {
        selector: "edge",
        style: {
            "width": 1,
            "curve-style": "unbundled-bezier",
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
    },
];
