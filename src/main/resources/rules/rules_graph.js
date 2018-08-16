"use strict";

function renderGraph() {
    // Build graph
    buildGraph();

    const container = $('#cy');
    container.empty();
    container.hide();

    // Render
    const cy = window.cy = cytoscape({
        container: container,
        elements: [].concat(Object.values(nodes), Object.values(edges)),
        style: RULE_GRAPH_STYLE("unbundled-bezier").slice()
    });
    cy.on('tap', 'edge', function (evt) {
        showInfo(evt.cyTarget.data());
    });
    cy.startBatch();
    cy.layout({
        name: 'cose-bilkent',
        animate: false,
        start: () => {
        },
        stop: () => {
            container.show();
            spinner.stop();
        },
        // Nesting factor (multiplier) to compute ideal edge length for inter-graph edges
        nestingFactor: 3,
        fit: true
    });
    cy.endBatch();
}

function buildGraph() {
    nodes = {};
    edges = [];

    function addNode(n) {
        console.info("NODE: " + n);
        let not_n = n.replace("NOT ", "");
        // Group nodes
        let n_group_id = not_n + "_group";
        nodes[n_group_id] = {
            data: {
                id: n_group_id
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
                data: {
                    id: not_edge_id,
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
        let width = Math.round(1 + 5 * record.conviction / convictionMax);
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
                data: {
                    id: id,
                    source: start,
                    target: end,
                    records: records,
                    width: width,
                },
                classes: classes,
                style: {
                    opacity: 0.3 + 0.5 * width / 5.0
                }
            };
        } else {
            edges[id] = {
                data: {
                    id: id,
                    source: start,
                    target: end,
                    records: [record],
                    width: width,
                },
                classes: classes,
                style: {
                    opacity: 0.3 + 0.5 * width / 5.0
                }
            };
        }
    }

    function process(r, target, processed) {
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
}
