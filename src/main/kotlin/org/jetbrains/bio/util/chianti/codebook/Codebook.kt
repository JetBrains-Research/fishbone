package org.jetbrains.bio.util.chianti.codebook

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jetbrains.bio.util.chianti.variable.DateVariable
import org.jetbrains.bio.util.chianti.variable.NominativeVariable
import org.jetbrains.bio.util.chianti.variable.NumericVariable
import org.jetbrains.bio.util.chianti.variable.Variable
import java.io.File

class Codebook(
        private val codebookFilename: String,
        private val irrelevantFeatures: Set<String>,
        private val redundantFeatures: Set<String>
) {

    val variables: Map<String, Variable>

    init {
        val workbook = WorkbookFactory.create(File(codebookFilename))
        val rowIterator = workbook.getSheetAt(0).rowIterator()
        // Skip header
        rowIterator.next()

        val sheetData = getSheetData(rowIterator, emptyList()).map { data -> dropExperimentPrefix(data) }
        variables = sheetData
                .asSequence()
                .filter { variableInfo -> isNotIrrelevantFeature(variableInfo) }
                .filter { variableInfo -> isNotRedundantFeature(variableInfo) }
                .map { variableInfo ->
                    when {
                        // If there is more then 1 like, than next lines contain codes for nominative variable
                        variableInfo.getValue(EpicCodebookColumn.Codes.index).size > 1 -> NominativeVariable.fromDataMap(variableInfo)
                        variableInfo.getValue(EpicCodebookColumn.Meaning.index)[0].toLowerCase().contains("date") ->
                            DateVariable.fromDataMap(variableInfo)
                        else -> NumericVariable.fromDataMap(variableInfo)
                    }
                }
                .map { it.name to it }
                .toMap()
    }

    private fun dropExperimentPrefix(data: Map<Int, List<String>>): Map<Int, List<String>> {
        return data.map { (key, value) ->
            if (key == EpicCodebookColumn.Variable.index) {
                Pair(key, value.map { Regex("""^(([AI][XYZQC])|[XYZQC]_)""").replaceFirst(it, "") })
            } else {
                Pair(key, value)
            }
        }.toMap()
    }

    private fun isNotIrrelevantFeature(data: Map<Int, List<String>>) =
            !irrelevantFeatures.contains(data.getValue(EpicCodebookColumn.Variable.index)[0])

    private fun isNotRedundantFeature(data: Map<Int, List<String>>) =
            !redundantFeatures.contains(data.getValue(EpicCodebookColumn.Variable.index)[0])

    private fun getSheetData(
            rowIterator: Iterator<Row>, data: List<Map<Int, List<String>>>
    ): List<Map<Int, List<String>>> {
        if (!rowIterator.hasNext()) {
            return data
        }
        val rowData = getRowData(rowIterator.next().cellIterator(), emptyMap())
        if (!rowData.containsKey(0) || rowData.getValue(0)[0] == "") {
            val updatedLastRow = addVariablesToLastRow(data, rowData)
            return getSheetData(rowIterator, data.subList(0, data.size - 1) + updatedLastRow)
        }
        return getSheetData(rowIterator, data + rowData)
    }

    private fun getRowData(cellIterator: Iterator<Cell>, data: Map<Int, List<String>>): Map<Int, List<String>> {
        if (!cellIterator.hasNext()) {
            return data
        }
        val cell = cellIterator.next()
        return getRowData(cellIterator, data + (cell.columnIndex to listOf(cell.toString())))
    }

    private fun addVariablesToLastRow(
            data: List<Map<Int, List<String>>>, rowData: Map<Int, List<String>>
    ): MutableMap<Int, List<String>> {
        val lastRow = data.last()
        val updatedLastRow = mutableMapOf<Int, List<String>>()
        updatedLastRow.putAll(lastRow)
        for (item in rowData) {
            if (lastRow.containsKey(item.key)) {
                updatedLastRow[item.key] = (lastRow.getValue(item.key) + item.value)
            }
        }
        return updatedLastRow
    }
}