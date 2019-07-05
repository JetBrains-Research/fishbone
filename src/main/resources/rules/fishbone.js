"use strict";

function renderFishBone() {
    // Build elements
    buildFishbone();

    const container = $('#cy');
    container.empty();
    container.hide();

    // Render
    const cy = window.cy = cytoscape({
        container: container,
        elements: [].concat(Object.values(nodes), Object.values(edges)),
        style: RULE_GRAPH_STYLE("haystack").slice(),
        layout: {name: 'preset'}
    });
    cy.on('tap', 'edge', function (evt) {
        showInfoEdge(evt.cyTarget.data());
    });
    cy.on('tap', 'node', function (evt) {
        showInfoNode(evt.cyTarget.data());
    });
    cy.fit();
    spinner.stop();
    container.show();
}

function buildFishbone() {
    nodes = {};
    edges = [];

    let nodeId = 0;

    function getWidth(criterionVal) {
        return Math.round(1 + 10 * criterionVal / criterionMax);
    }

    function addEdge(start, end, record, classes) {
        let width = getWidth(record[criterion]);
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
                    opacity: 0.3 + 0.5 * width / 10.0
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
                    opacity: 0.3 + 0.5 * width / 10.0
                }
            };
        }
        return id;
    }

    function addBone(start, end, r) {
        if (!r) {
            let id = edgeId(start, end);
            edges[id] = {
                data: {
                    id: id,
                    source: start,
                    target: end,
                    width: 1,
                },
                classes: CLASS_MISSING_RULE
            };
            return id
        }
        let classes = CLASS_MISSING_RULE;
        if (r['operator'] == 'and' && filter_record(r)) {
            classes = CLASS_AND;
        } else if (['operator'] == 'or' && filter_record(r)) {
            classes = CLASS_OR;
        }
        return addEdge(start, end, r, classes);
    }

    function addNode(target, n, x, y) {
        console.info("NODE: " + target + "-" + n);
        const id = target + "_" + nodeId + "_" + n;
        nodeId += 1;
        let not_n = n.replace("NOT ", "");
        if (!(id in nodes)) {
            if (not_n in palette) {
                let classes = "colored";
                if (n !== not_n) {
                    classes = "colored_not";
                }
                nodes[id] = {
                    data: {
                        id: id,
                        label: n,
                        text_color: textColor(palette[not_n]),
                        background_color: palette[not_n],
                    },
                    position: {
                        x: x,
                        y: y
                    },
                    classes: classes
                };
            } else {
                nodes[id] = {
                    data: {
                        id: id,
                        label: "",
                    },
                    position: {
                        x: x,
                        y: y
                    }
                };
            }
        }
        return id;
    }


    const fishes = new Set(filteredRecords.map(el => el.target)).size;
    const fishLength = (Math.min($(window).width(), $(window).height()) - 100) / fishes;
    const xTail = $(window).width() / 2 - fishLength / 2;
    const xHead = $(window).width() / 2 + fishLength / 2;



    function renderFish(target, fish, boneLength, headId, xHead, yHead, boneAngle) {
        let bones = Object.values(fish.bones);
        bones.sort(function (a, b) {
            return b[criterion] - a[criterion];
        });
        let chunks = bones.length + 1;
        const delta = Math.max(30, boneLength / chunks);
        let direction = 1; // up
        let lastBoneStartNode = headId;
        let x = xHead;
        let y = yHead;
        for (let bone of bones) {
            x = x + Math.cos(boneAngle) * delta;
            y = y + Math.sin(boneAngle) * delta;
            const boneStartNode = addNode(target, "", x, y);
            const boneId = addBone(boneStartNode, lastBoneStartNode, null);
            edges[boneId]['data']['width'] = getWidth(bone[criterion]);
            lastBoneStartNode = boneStartNode;
            let boneEndAngle;
            if (boneAngle === Math.PI) {
                boneEndAngle = Math.PI + direction * 3 * Math.PI / 8;
            } else {
                boneEndAngle = Math.PI;
            }
            let childBoneLength = Math.max(80, 9 * boneLength / 20);
            // Don't mix nodes
            const boneEndNode = addNode(target, bone.node,
                x + Math.cos(boneEndAngle) * childBoneLength,
                y + Math.sin(boneEndAngle) * childBoneLength);

            let lastBoneStartNodeRec = renderFish(target, bone, childBoneLength, boneStartNode, x, y, boneEndAngle);

            addBone(boneEndNode, lastBoneStartNodeRec, bone.records[0]);
            direction = -direction;
        }
        return lastBoneStartNode;
    }

    /**
     * Builds fish using DFS
     */
    function buildFish(target, fishRecords) {
        const fish = {
            bones: {}
        };
        for (let r of fishRecords) {
            let path = [];
            let currentRecord = r;
            while (true) {
                path.push(currentRecord);
                if (currentRecord.hasOwnProperty('parent_node')) {
                    // Find parent record
                    currentRecord = records.filter(el => el.id === currentRecord.id &&
                        el.target === target &&
                        el.condition === currentRecord.parent_condition)[0]
                } else {
                    break
                }
            }
            let currentFish = fish;
            for (let rec of path.reverse()) {
                const node = rec.node;
                if (node in currentFish.bones) {
                    currentFish = currentFish.bones[node];
                    currentFish[criterion] = Math.max(currentFish[criterion], r[criterion]);
                    currentFish.records.push(rec);
                } else {
                    currentFish.bones[node] = {
                        node: node,
                        bones: {},
                        records: [rec]
                    };
                    currentFish.bones[node][criterion] = r[criterion];
                    currentFish = currentFish.bones[node];
                }
            }
        }
        // console.info(JSON.stringify(fish));
        return fish;
    }

    // Each target dedicates it's own Fish
    let fishY = fishLength / 2;
    for (let target of new Set(filteredRecords.map(el => el.target))) {
        let fishRecords = filteredRecords.filter(el => el.target === target);
        fishRecords.sort(function (a, b) {
            return b[criterion] - a[criterion];
        });
        const fish = buildFish(target, fishRecords);
        const headId = addNode(target, target, xHead, fishY);
        nodes[headId]['style'] = {
            shape: 'polygon',
            height: 50,
            'shape-polygon-points': [-1, 0.5, -0.8, 0.8, 0.4, 0.8, 1, 0.2, 1, -0.2, 0.4, -0.8, -0.8, -0.8, -1, -0.5],
        };
        let boneLength = xHead - xTail;
        const lastBoneStartNode = renderFish(target, fish, boneLength, headId, xHead, fishY, Math.PI);
        const tailId = addNode(target, "tail",
            xHead + Math.cos(Math.PI) * boneLength,
            fishY + Math.sin(Math.PI) * boneLength);
        nodes[tailId]['style'] = {
            shape: 'polygon',
            width: 50,
            height: 50,
            'shape-polygon-points': [-1, -1, 1, 0, -1, 1],
            'background-color': '#f8ac00'
        };

        const tailBone = addBone(tailId, lastBoneStartNode, null);
        edges[tailBone]['data']['width'] = getWidth(criterionMax);
        fishY = fishY + boneLength;
    }
}
