"use strict";

// Adapted from https://bl.ocks.org/dorothy32/ef877f9bba78dee8fcaa052d972a6016
function drawHeatMap(divId, tableData, xRootData, yRootData,
                     cell = 10,
                     font = 11) {
    // Compute margins
    let longestLabelCol = 0;
    let longestLabelRow = 0;
    for (let d of tableData) {
        longestLabelCol = Math.max(longestLabelCol, d.key.length);
        for (let v of d.values) {
            longestLabelRow = Math.max(longestLabelRow, v.key.length);
        }
    }
    const marginCols = longestLabelCol * font / 2;
    const marginRows = longestLabelRow * font / 2;

    const nCols = tableData[0].values.length;
    const nRows = tableData.length;
    const linkHeight = cell * Math.max(Math.log2(nRows), Math.log2(nCols));

    const heatmapWidth = nCols * cell;
    const heatmapHeight = nRows * cell;
    const width = linkHeight + heatmapWidth + marginCols + 40;
    const height = linkHeight + heatmapHeight + marginRows + 40;

    const div = $(`#${divId}`);
    const tooltipDx = div.position().left + 30;
    const tooltipDy = div.position().top + 20;
    var tooltip = d3.select(`#${divId}`).append('div').attr('class', 'tooltip');

    const svg = d3.select(`#${divId}`)
        .append("svg")
        .attr('width', width)
        .attr('height', height);


    const heatmap = svg.append('g').attr('class', 'heatmap');


    const bandX = d3.scaleBand()
        .domain(d3.range(nCols))
        .range([0, heatmapWidth]);
    const bandY = d3.scaleBand()
        .domain(d3.range(nRows))
        .range([0, heatmapWidth]);

    const heatColor = d3.scaleLinear()
        .domain([-1.0, 0.0, 1.0])
        .range(['#0000ff', '#ffffff', '#ff0000']);
    const table = heatmap.append('g').attr('class', 'table')
        .attr('transform', 'translate(' + (linkHeight + 10) + ',' + (linkHeight + 10) + ')');
    const rows = table.selectAll('.row')
        .data(tableData)
        .enter().append('g')
        .attr('class', 'row')
        .attr('transform', function (d, i) {
            return 'translate(0, ' + bandY(i) + ')';
        });

    var selectedCell;
    rows.selectAll('rect')
        .data(function (d) {
            d.values.map(function (i) {
                return i.parent = d.key
            });
            return d.values;
        })
        .enter().append('rect')
        .style('fill', function (d) {
            return heatColor(d.value)
        })
        .style('opacity', 0.9)
        .attr('x', function (d, i) {
            return bandX(i);
        })
        .attr('width', bandX.bandwidth())
        .attr('height', bandY.bandwidth())
        .on('mousemove', function (d) {
            selectedCell = {row: d.parent, col: d.key, value: d.value};
            const message = selectedCell.value.toFixed(2) + ": " + selectedCell.row + ", " + selectedCell.col;
            tooltip
                .style("left", (d3.event.offsetX + tooltipDx) + "px")
                .style("top", (d3.event.offsetY + tooltipDy) + "px")
                .style("width", Math.max(150, message.length * font / 1.5 + 10) + "px");
            tooltip.text(message);
        })
        .on('mouseover', function (d) {
            tooltip.style('opacity', 1);
            d3.select(this)
                .style('opacity', 1)
                .style('stroke', '#000')
                .style('stroke-width', 2);
        })
        .on('mouseout', function (d) {
            selectedCell = null;
            tooltip.style('opacity', 0);
            d3.select(this)
                .style('opacity', 0.9)
                .style('stroke-width', 0);
        });


    const yRoot = d3.hierarchy(yRootData)
        .sum(function (d) {
            return d.length;
        });

    setYLinkScaledY(yRoot, yRoot.data.length = 0, linkHeight / yRoot.data.totalLength);

    function setYLinkScaledY(d, y0, k) {
        d.yLinkScaledY = (y0 += d.data.length) * k;
        if (d.children) d.children.forEach(function (d) {
            setYLinkScaledY(d, y0, k);
        });
    }

    const yCluster = d3.cluster()
        .size([heatmapWidth, linkHeight])
        .separation(function () {
            return 1;
        });

    yCluster(yRoot);

    const yLinks = heatmap.append('g').attr('class', 'ylinks')
        .attr('transform', 'translate(' + 0 + ',' + (linkHeight + 10) + ')');
    yLinks.selectAll('.link')
        .data(yRoot.descendants().slice(1))
        .enter().append('path')
        .attr('class', 'link')
        .style('fill', 'none')
        .style('stroke', '#000')
        .style('stroke-width', 1)
        .attr("d", function (d) {
            return "M" + d.yLinkScaledY + "," + d.x
                + "L" + d.parent.yLinkScaledY + "," + d.x
                + " " + d.parent.yLinkScaledY + "," + d.parent.x;
        });

    const yNodes = heatmap.append('g').attr('class', 'ynodes')
        .style('font-size', font)
        .attr('transform', 'translate(' + (heatmapWidth + 10 + 10) + ',' + (linkHeight + 10 + 4) + ')');
    yNodes.selectAll('.y-node')
        .data(yRoot.descendants())
        .enter().append('text')
        .attr('class', function (d) {
            return 'y-node ' + (d.data.key ? d.data.key : '')
        })
        .attr('transform', function (d) {
            return 'translate(' + d.y + ',' + d.x + ')';
        })
        .text(function (d) {
            return d.children ? '' : d.data.key
        });


    const xRoot = d3.hierarchy(xRootData)
        .sum(function (d) {
            return d.length;
        });

    setXLinkScaledY(xRoot, xRoot.data.length = 0, linkHeight / xRoot.data.totalLength);

    function setXLinkScaledY(d, y0, k) {
        d.xLinkScaledY = (y0 += d.data.length) * k;
        if (d.children) d.children.forEach(function (d) {
            setXLinkScaledY(d, y0, k);
        });
    }

    const xCluster = d3.cluster()
        .size([heatmapWidth, linkHeight])
        .separation(function () {
            return 1;
        });

    xCluster(xRoot);

    const xLinks = heatmap.append('g').attr('class', 'xlinks')
        .attr('transform', 'translate(' + (linkHeight + 10) + ',' + 0 + ')');
    xLinks.selectAll('.link')
        .data(xRoot.descendants().slice(1))
        .enter().append('path')
        .attr('class', 'link')
        .style('fill', 'none')
        .style('stroke', 'blue')
        .style('stroke-width', 1)
        .attr("d", function (d) {
            return "M" + d.x + "," + d.xLinkScaledY
                + "L" + d.x + "," + d.parent.xLinkScaledY
                + " " + d.parent.x + "," + d.parent.xLinkScaledY;
        });

    const xNodes = heatmap.append('g').attr('class', 'xnodes')
        .style('font-size', font)
        .attr('transform',
            'translate(' + (linkHeight + 10 - 5) + ',' + (heatmapWidth + 10 + 20) + ')');
    xNodes.selectAll('.x-node')
        .data(xRoot.descendants())
        .enter().append('text')
        .attr('class', 'x-node')
        .style("text-anchor", 'start')
        .attr("transform", function (d) {
            return "translate(" + d.x + "," + d.y + ") rotate(45)";
        })
        .text(function (d) {
            return d.children ? '' : d.data.key
        });
}
