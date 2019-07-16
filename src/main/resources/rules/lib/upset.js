"use strict";

// Adopted from https://github.com/chuntul/d3-upset
// Simplified and converted to D3JS v4


function visualizeUpset(div, labels, data,
                        height = 600,
                        marginLeft = 100,
                        rad = 7,
                        font = 9,
                        step = 2.2) {
    //position and dimensions
    const width = rad * step * data.length + 5;
    const barsHeight = height - labels.length * rad * (step + 1);

    // make the canvas
    const svg = d3.select("#" + div)
        .append("svg")
        .attr("width", width + marginLeft)
        .attr("height", height)
        .attr("xmlns", "http://www.w3.org/2000/svg")
        .attr("xmlns:xlink", "http://www.w3.org/1999/xlink")
        .append("g")
        .attr("transform",
            "translate(" + marginLeft + ",10)")
        .attr("fill", "white");

    // make a group for the upset circle intersection things
    const upsetCircles = svg.append("g")
        .attr("id", "upsetCircles")
        .attr("transform", "translate(" + rad + "," + (barsHeight + rad + 10) + ")");

    // making dataset labels
    for (let i = 0; i < labels.length; i++) {
        upsetCircles.append("text")
            .attr("dx", -rad - 10)
            .attr("dy", font / 2 + i * (rad * step))
            .attr("text-anchor", "end")
            .attr("fill", "black")
            .style("font-size", font)
            .text(labels[i])
    }

    // sort data decreasing
    data.sort(function (a, b) {
        return parseInt(b.n) - parseInt(a.n);
    });


    //set range for data by domain, and scale by range
    const xrange = d3.scaleLinear()
        .domain([0, data.length])
        .range([0, rad * step * data.length]);

    //set log scale in case of big max/min ratio
    let yrange;
    if (data[0].n / data[data.length - 1].n <= 100) {
        yrange = d3.scaleLinear()
            .domain([0, data[0].n])
            .range([barsHeight, 0]);
    } else {
        yrange = d3.scaleLog()
            .domain([data[data.length - 1].n, data[0].n])
            .range([barsHeight, 0]);
    }

    //set axes for graph
    const xAxis = d3.axisBottom()
        .scale(xrange)
        .tickFormat(function (d, i) {
            return data[i].id
        })
        .tickValues(d3.range(data.length));

    const yAxis = d3.axisLeft()
        .scale(yrange)
        .tickFormat(d3.format("d"));

    // make the bars
    const upsetBars = svg.append("g")
        .attr("id", "upsetBars");

    //add X axis
    upsetBars.append("g")
        .attr("class", "x axis")
        .attr("transform", "translate(0," + barsHeight + ")")
        .attr("fill", "none")
        .attr("stroke", "black")
        .attr("stroke-width", 1)
        .call(xAxis)
        .selectAll(".tick")
        .remove();

    // Add the Y Axis
    upsetBars.append("g")
        .attr("class", "y axis")
        .attr("fill", "none")
        .attr("stroke", "black")
        .attr("stroke-width", 1)
        .call(yAxis)
        .selectAll("text")
        .attr("fill", "black")
        .attr("stroke", "none");

    const chart = upsetBars.append('g')
        .attr("transform", "translate(1,0)")
        .attr('id', 'chart');

    // bars
    chart.selectAll('.bar')
        .data(data)
        .enter()
        .append('rect')
        .attr("class", "bar")
        .attr('width', rad * 2)
        .style('fill', "darkslategrey")
        .attrs({
            'x': function (d, i) {
                return i * (rad * step)
            },
            'y': function (d) {
                return yrange(d.n)
            },
            'height': function (d) {
                return barsHeight - yrange(d.n);
            }
        });

    for (let i = 0; i < data.length; i++) {
        //circles
        for (let j = 0; j < labels.length; j++) {
            upsetCircles.append("circle")
                .attr("cx", i * (rad * step))
                .attr("cy", j * (rad * step))
                .attr("r", rad)
                .attr("id", "id" + i)
                .style("opacity", 1)
                .attr("fill", function () {
                    if (data[i].id.indexOf(j) !== -1) {
                        return "darkslategrey"
                    } else {
                        return "silver"
                    }
                })

        }
        // lines
        if (data[i].id.length !== 1) {
            upsetCircles.append("line")
                .attr("id", "setline" + i)
                .attr("x1", i * (rad * step))
                .attr("y1", data[i].id[0] * (rad * step))
                .attr("x2", i * (rad * step))
                .attr("y2", data[i].id[data[i].id.length - 1] * (rad * step))
                .style("stroke", "darkslategrey")
                .attr("stroke-width", 4)

        }
    }
}

function showInfo() {
    const labels = [
        "set111111111111111111111111111",
        "set2",
        "set33333",
        "set44444444444444444444444444444444444444444444444",];
    const sets = [
        ["a", "b", "c", "d"],
        ["a", "b", "c", "d", "e", "f"],
        ["a", "b", "g", "h", "i"],
        ["a", "i", "j", "c", "d"],
    ];

    visualizeUpset('upset', labels, makeUpset(sets));
}

// takes two arrays of values and returns an array of intersecting values
function findIntersection(set1, set2) {
    //see which set is shorter
    let temp;
    if (set2.length > set1.length) {
        // swap
        temp = set2, set2 = set1, set1 = temp;
    }

    return set1
        .filter(function (e) { //puts in the intersecting names
            return set2.indexOf(e) > -1;
        })
        .filter(function (e, i, c) { // gets rid of duplicates
            return c.indexOf(e) === i;
        })
}

//for the difference of arrays - particularly in the intersections and middles
//does not mutate any of the arrays
Array.prototype.diff = function (a) {
    return this.filter(function (i) {
        return a.indexOf(i) < 0;
    });
};

//for calculating solo datasets
function subtractUpset(i, inds, names) {
    const result = names[i].slice(0);
    for (let ind = 0; ind < inds.length; ind++) {
        // set1 vs set2 -> names[i] vs names[ind]
        for (let j = 0; j < names[inds[ind]].length; j++) { // for each element in set2
            if (result.includes(names[inds[ind]][j])) {
                // if result has the element, remove the element
                // else, ignore
                const index = result.indexOf(names[inds[ind]][j]);
                if (index > -1) {
                    result.splice(index, 1)
                }
            }
        }
    }
    return result
}

//recursively gets the intersection for each dataset
function helperUpset(start, end, numSets, names, data) {
    if (end === numSets) {
        return data
    } else {
        const intSet = {
            "id": data[data.length - 1].id + [end],
            "items": findIntersection(data[data.length - 1].items, names[end])
        };
        data.push(intSet);
        return helperUpset(start, end + 1, numSets, names, data)
    }
}

function makeUpset(sets) {
    //number of circles to make
    // computes intersections
    let data2 = [];

    for (let i = 0; i < sets.length; i++) {
        // Push single combination
        data2.push({
            "id": [i],
            "items": sets[i]
        });

        for (let j = i + 1; j < sets.length; j++) {
            data2.push({
                "id": [i, j],
                "items": findIntersection(sets[i], sets[j])
            });
            helperUpset(i, j + 1, sets.length, sets, data2)
        }
    }

    //removing all solo datasets and replacing with data just in those datasets (cannot intersect with others)
    const tempData = [];
    for (let i = 0; i < data2.length; i++) {
        if (data2[i].id.length !== 1) { // solo dataset
            tempData.push(data2[i])
        }
    }
    data2 = tempData;

    for (let i = 0; i < sets.length; i++) {
        const inds = Array.apply(null, {length: sets.length}).map(Function.call, Number);
        const index = inds.indexOf(i);
        if (index > -1) {
            inds.splice(index, 1);
        }
        const result = subtractUpset(i, inds, sets);
        data2.push({
            "id": [i],
            "items": sets[i]
        })
    }


    // makes sure data is unique
    const unique = [];
    const newData = [];
    for (let i = 0; i < data2.length; i++) {
        if (unique.indexOf(data2[i].id) === -1) {
            unique.push(data2[i].id);
            newData.push({
                "id": data2[i].id,
                "n": data2[i].items.length
            })
        }
    }
    return newData;
}
