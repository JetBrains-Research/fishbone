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
        showInfo(evt.cyTarget.data());
    });
    cy.fit();
    spinner.stop();
    container.show();
}

function buildFishbone() {
    nodes = {};
    edges = [];

    let nodeId = 0;

    function addEdge(start, end, record, classes) {
        let width = Math.round(1 + 10 * record.conviction / convictionMax);
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
    }

    function addBone(start, end, r) {
        if (!r) {
            let tailEdge = edgeId(start, end);
            edges[tailEdge] = {
                data: {
                    id: tailEdge,
                    source: start,
                    target: end
                }
            };
            return
        }
        if (r.hasOwnProperty("parent_node")) {
            // Show target link in case we don't have any direct path
            let classes = CLASS_MISSING_RULE;
            if (filter_record(r)) {
                classes = CLASS_PARENT_CHILD;
            }
            addEdge(start, end, r, classes);
        } else {
            let classes;
            if (filter_record(r)) {
                classes = CLASS_CONDITION_TARGET;
            } else {
                classes = CLASS_MISSING_RULE;
            }
            addEdge(start, end, r, classes);
        }
    }

    function addNode(fish, n, x, y) {
        console.info("NODE: " + fish + "-" + n);
        const id = fish + "_" + nodeId + "_" + n;
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
                        color: palette[not_n],
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



    function renderFish(fish, boneLength, headId, xHead, yHead, boneAngle) {
        let bones = Object.values(fish.bones);
        bones.sort(function (a, b) {
            return b.conviction - a.conviction;
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
            const boneStartNode = addNode(fish, "", x, y);
            addBone(boneStartNode, lastBoneStartNode, null);
            lastBoneStartNode = boneStartNode;
            let boneEndAngle;
            if (boneAngle === Math.PI) {
                boneEndAngle = Math.PI + direction * 3 * Math.PI / 8;
            } else {
                boneEndAngle = Math.PI;
            }
            let childBoneLength = Math.max(80, 9 * boneLength / 20);
            // Don't mix nodes
            const boneEndNode = addNode(fish, bone.node,
                x + Math.cos(boneEndAngle) * childBoneLength,
                y + Math.sin(boneEndAngle) * childBoneLength);

            let lastBoneStartNodeRec = renderFish(bone, childBoneLength, boneStartNode, x, y, boneEndAngle);

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
                    currentFish.conviction = Math.max(currentFish.conviction, r.conviction);
                    currentFish.records.push(rec);
                } else {
                    currentFish.bones[node] = {
                        node: node,
                        bones: {},
                        conviction: r.conviction,
                        records: [rec]
                    };
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
            return b.conviction - a.conviction;
        });
        const fish = buildFish(target, fishRecords);
        const headId = addNode(target, target, xHead, fishY);
        let boneLength = xHead - xTail;
        const lastBoneStartNode = renderFish(fish, boneLength, headId, xHead, fishY, Math.PI);
        const tailId = addNode(target, "tail",
            xHead + Math.cos(Math.PI) * boneLength,
            fishY + Math.sin(Math.PI) * boneLength);
        addBone(tailId, lastBoneStartNode, null);
        fishY = fishY + boneLength;
    }
}
