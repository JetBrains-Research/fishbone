package org.jetbrains.bio.util.chianti

import com.epam.parso.impl.SasFileReaderImpl
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jetbrains.bio.predicates.OveralpSamplePredicate
import org.jetbrains.bio.util.chianti.model.*
import java.io.File
import java.io.FileInputStream
import java.time.ZoneId
import java.util.*

/**
 * this class is used to parse labo_raw data from chianti dataset
 */
class LaboratoryDataParser {
    val dataFilename =
        "/home/nlukashina/education/bioinf/spring/fishbone_materials/Articles/censored_data/english/4.data/sas_datasets/Assays/labo_raw.sas7bdat"
    private val codebookFilename =
        "/home/nlukashina/education/bioinf/spring/fishbone_materials/Articles/censored_data/english/3.Codebooks/labo_raw_fixed.xlsx"
    private val ignoredVariables = setOf(
        "CODE98", // will be used only as sample id
        "SITE",
        "DATA_NAS",
        "X_DATEL",
        "X_VUOTO",
        "X_PIENO",
        "X_BUSTE",
        "X_INIZIO",
        "X_FINE",
        "X_PPAIR",
        "X_CASCTL",
        "X_U_SEDI",
        "X_USEDIA"
    )

    fun readCodebook(): Codebook {
        val workbook = WorkbookFactory.create(File(codebookFilename))
        val rowIterator = workbook.getSheetAt(0).rowIterator()
        // Skip header
        rowIterator.next()

        val sheetData = getSheetData(rowIterator, emptyList())
        val variables = sheetData
            // filter ignoring variables
            .filter { data -> !ignoredVariables.contains(data.getValue(EpicCodebookColumn.Variable.index)[0]) }
            .map { data ->
                println(data.getValue(EpicCodebookColumn.Variable.index)[0])
                when {
                    data.getValue(EpicCodebookColumn.Codes.index).size > 1 -> EncodedVariable.fromDataMap(data)
                    data.getValue(EpicCodebookColumn.Meaning.index)[0].toLowerCase().contains("date") ->
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
        if (!rowData.containsKey(0) || rowData.getValue(0)[0] == "") {
            val updatedLastRow = addVariablesToLastRow(data, rowData)
            return getSheetData(rowIterator, data.subList(0, data.size - 1) + updatedLastRow)
        }
        return getSheetData(rowIterator, data + rowData)
    }

    // TODO: rewrite with streams
    private fun addVariablesToLastRow(
        data: List<Map<Int, List<String>>>,
        rowData: Map<Int, List<String>>
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
                println(column)
                /*if (column == "X_UTPEGE") {
                    println("f")
                }*/
                val cell = sample[columnIndex]
                if (cell != null) { // could be missed data
                    val satisfiedPredicates =
                        predicateCodebooks
                            .filter { (name, predicate) ->
                                name.contains(column) && predicate(
                                    if (cell is Date) cell.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() else cell.toString()
                                )
                            }
                            .keys
                    for (name in satisfiedPredicates) {
                        dataPredicates[name] = (dataPredicates.getOrDefault(name, emptyList()) + sampleIndex)
                    }
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
        private val defaultDataoutputFolder =
            "/home/nlukashina/education/bioinf/spring/fishbone_materials/chianti_experiments_medical_to_age"

        @JvmStatic
        fun main(args: Array<String>) {

            val processor = LaboratoryDataParser()
            val codebook = processor.readCodebook()
            val (database, predicates) = processor.createPredicatesFromData(
                CodebookToPredicatesTransformer(codebook).predicates
            )
            val databaseOutput = File("$defaultDataoutputFolder/database.txt")
            databaseOutput.createNewFile()
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
        }
    }
}
