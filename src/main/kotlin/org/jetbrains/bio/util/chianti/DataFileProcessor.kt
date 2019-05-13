package org.jetbrains.bio.util.chianti

import com.epam.parso.impl.SasFileReaderImpl
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.experiments.rules.ChiantiDataExperiment
import org.jetbrains.bio.predicates.OveralpSamplePredicate
import org.jetbrains.bio.util.chianti.model.*
import java.io.File
import java.io.FileInputStream
import java.time.ZoneId
import java.util.*


class DataFileProcessor {

    val dataFilename =
        "/home/nlukashina/education/bioinf/spring/fishbone_materials/Articles/censored_data/english/4.data/sas_datasets/Nutrients_Intake/epic_raw.sas7bdat"
    val codebookFilename =
        "/home/nlukashina/education/bioinf/spring/fishbone_materials/Articles/censored_data/english/3.Codebooks/epic_raw.xlsx"

    fun readCodebook(): Codebook {
        val workbook = WorkbookFactory.create(File(codebookFilename))
        val rowIterator = workbook.getSheetAt(0).rowIterator()
        // Skip header
        rowIterator.next()

        val sheetData = getSheetData(rowIterator, emptyList())
        val variables = sheetData.map { data ->
            when {
                data.getValue(CodebookColumn.Codes.index).size > 1 -> EncodedVariable.fromDataMap(data)
                data.getValue(CodebookColumn.Meaning.index)[0].toLowerCase().contains("date") ->
                    DateVariable.fromDataMap(data)
                else -> NumericVariable.fromDataMap(data)
            }
        }.toList()
        return Codebook(variables.map { it.name to it }.toMap())
    }

    private fun getSheetData(
        rowIterator: Iterator<Row>,
        data: List<Map<Int, List<String>>>
    ): List<Map<Int, List<String>>> {
        if (!rowIterator.hasNext()) {
            return data
        }
        val rowData = getRowData(rowIterator.next().cellIterator(), emptyMap())
        if (!rowData.containsKey(0)) {
            val updatedLastRow = addVariablesToLastRow(data, rowData)
            return getSheetData(rowIterator, data.subList(0, data.size - 1) + updatedLastRow)
        }
        return getSheetData(rowIterator, data + rowData)
    }

    private fun addVariablesToLastRow(
        data: List<Map<Int, List<String>>>,
        rowData: Map<Int, List<String>>
    ): MutableMap<Int, List<String>> {
        val lastRow = data.last()
        // TODO: rewrite with streams
        val updatedLastRow = mutableMapOf<Int, List<String>>()
        updatedLastRow.putAll(lastRow)
        for (item in rowData) {
            if (lastRow.containsKey(item.key)) {
                updatedLastRow[item.key] = (lastRow.getValue(item.key) + item.value)
            }
        }
        return updatedLastRow
    }

    private fun getRowData(cellIterator: Iterator<Cell>, data: Map<Int, List<String>>): Map<Int, List<String>> {
        if (!cellIterator.hasNext()) {
            return data
        }
        val cell = cellIterator.next()
        return getRowData(cellIterator, data + (cell.columnIndex to listOf(cell.toString())))
    }

    fun createPredicatesFromData(predicateCodebooks: Map<String, (Any) -> Boolean>)
            : Pair<List<Int>, List<OveralpSamplePredicate>> {
        val sasFileReader = SasFileReaderImpl(FileInputStream(dataFilename))
        val columnsByIndex = sasFileReader.columns.withIndex().map { (idx, column) -> idx to column.name }.toMap()
        val data = sasFileReader.readAll() // todo: readNext

        val dataPredicates = mutableMapOf<String, List<Int>>()
        val database = data.withIndex().map { (sampleIndex, sample) ->
            println(sampleIndex)
            for ((columnIndex, column) in columnsByIndex) {
                var cell = sample[columnIndex]
                if (cell is Date) {
                    cell = cell.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                }
                val satisfiedPredicates =
                    predicateCodebooks
                        .filter { (name, predicate) -> name.contains(column) && predicate(cell) }
                        .keys
                for (name in satisfiedPredicates) {
                    dataPredicates[name] = (dataPredicates.getOrDefault(name, emptyList()) + sampleIndex)
                }
            }
            sampleIndex
        }
        return Pair(database, dataPredicates.map {
            OveralpSamplePredicate(
                it.key,
                it.value
            )
        })
    }

    companion object {
        private val defaultDataoutputFolder = "/home/nlukashina/education/bioinf/spring/fishbone_materials/italiantest"

        @JvmStatic
        fun main(args: Array<String>) {

            val processor = DataFileProcessor()
            val codebook = processor.readCodebook()
            val transformer = CodebookToPredicatesTransformer(codebook)
            val (database, predicates) = processor.createPredicatesFromData(transformer.predicates)
            val databaseOutput = File("$defaultDataoutputFolder/database.txt")
            databaseOutput.printWriter().use { out ->
                database.forEach { out.println(it) }
            }
            val predicateFilenames = predicates.map { predicate ->
                val output = File("$defaultDataoutputFolder/${predicate.name}")
                output.printWriter().use { out ->
                    predicate.samples.forEach { out.println(it) }
                }
                output.absolutePath
            }

            predicateFilenames.forEach { println(it) }

            /*ChiantiDataExperiment().run(
                MineRulesRequest(
                    "itailan",
                    predicateFilenames.subList(1, predicateFilenames.size),
                    listOf(predicateFilenames[0]),
                    databaseOutput.absolutePath
                )
            )*/
        }
    }
}