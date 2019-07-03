package org.jetbrains.bio.util.chianti

import com.epam.parso.impl.SasFileReaderImpl
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jetbrains.bio.predicates.OverlapSamplePredicate
import org.jetbrains.bio.util.chianti.model.*
import org.jetbrains.bio.util.toPath
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.time.ZoneId
import java.util.*


/**
 * This class is used to parse labo_raw data from chianti dataset
 */
class LaboratoryDataParser {
    val dataFilename =
        "/home/nlukashina/education/bioinf/spring/fishbone_materials/Articles/censored_data/english/4.data/sas_datasets/Assays/labo_raw.sas7bdat"
    private val codebookFilename =
        "/home/nlukashina/education/bioinf/spring/fishbone_materials/Articles/censored_data/english/3.Codebooks/labo_raw_fixed.xlsx"
    private val referenceFilename = "/home/nlukashina/education/bioinf/spring/fishbone_materials/qualified_features/csv_numerical_references/ref_bl.csv"
    private val ignoredVariables = setOf(
        "CODE98", // will be used only as sample id
        "SITE",
        "DATA_NAS",
        "DATEL",
        "VUOTO",
        "PIENO",
        "BUSTE",
        "INIZIO",
        "FINE",
        "PPAIR",
        "CASCTL",
        "U_SEDI",
        "USEDIA",
        "LNOTES",
        "LCODE",
        "U_CLAR",
        "U_COLO",
        "U_NOTE"
    )

    fun readCodebook(): Codebook {
        val workbook = WorkbookFactory.create(File(codebookFilename))
        val rowIterator = workbook.getSheetAt(0).rowIterator()
        // Skip header
        rowIterator.next()

        val sheetData = getSheetData(rowIterator, emptyList()).map { data ->
            data.map { (key, value) ->
                if (key == EpicCodebookColumn.Variable.index) {
                    Pair(key, value.map { Regex("""^[XYZQC]_""").replaceFirst(it,"") })
                } else {
                    Pair(key, value)
                }
            }.toMap()
        }
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

    fun readReferences(): List<CSVRecord> {
        val reader = Files.newBufferedReader(referenceFilename.toPath())
        return CSVParser(reader, CSVFormat.DEFAULT.withDelimiter('\t')).map { csvRecord ->
            csvRecord
        }
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
            : Pair<List<Int>, List<OverlapSamplePredicate>> {
        val sasFileReader = SasFileReaderImpl(FileInputStream(dataFilename))
        val data = sasFileReader.readAll() // todo: readNext
        val ageColumnIndex = sasFileReader.columns.withIndex().first { Regex("""^[XYZQC]_AGEL""")
                .matches(it.value.name) }.index
        val columnsByIndex = sasFileReader.columns.withIndex().map { (idx, column) -> idx to
                Regex("""^[XYZQC]_""").replaceFirst(column.name, "") }.filter { (idx, _) ->
            val youngData = data.filter { it[ageColumnIndex].toString().toInt() < 40 }
            val oldData = data.filter { it[ageColumnIndex].toString().toInt() in 65..75 }
            youngData.map { if(it[idx] != null) 1 else 0 }.sum() >= 0.8 * youngData.size &&
                    oldData.map { if(it[idx] != null) 1 else 0 }.sum() >= 0.8 * oldData.size
        }.toMap()

        val dataPredicates = mutableMapOf<String, List<Int>>()
        val database = data.withIndex().map { (sampleIndex, sample) ->
            println(sampleIndex)
            for ((columnIndex, column) in columnsByIndex) {
//                println(column)
                val cell = sample[columnIndex]
                if (cell != null) { // could be missed data
                    val satisfiedPredicates =
                        predicateCodebooks
                            .filter { (name, predicate) ->
                                ("(low|high|normal|above_ref|below_ref|inside_ref)_$column".toRegex().matches(name) ||
                                        (!"(low|high|normal|above_ref|below_ref|inside_ref).*".toRegex().matches(name)
                                                && name.contains(column))) &&
                                        !(column == "AGEL" && !(cell.toString().toDouble() in 65.0..75.0 ||
                                                cell.toString().toDouble() < 40)) &&
//                                        !(column == "SEX" && cell.toString().toInt() == 2) &&
                                        predicate(if (cell is Date)
                                                      cell.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                                                  else cell.toString())
                            }
                            .keys
                    for (name in satisfiedPredicates) {
                        dataPredicates[name] = (dataPredicates.getOrDefault(name, emptyList()) + sampleIndex)
                    }
                }
            }
            sampleIndex
        }.filter { idx -> data[idx][5].toString().toDouble() in 65.0..75.0 || data[idx][5].toString().toDouble() < 40 }

        return Pair(database, dataPredicates.map {
            OverlapSamplePredicate(
                it.key,
                it.value
            )
        })
    }

    companion object {
        private val defaultDataoutputFolder =
            "/home/nlukashina/education/bioinf/spring/fishbone_materials/chianti_experiments_with_ref"

        @JvmStatic
        fun main(args: Array<String>) {
            val processor = LaboratoryDataParser()
            val codebook = processor.readCodebook()
            val references = processor.readReferences()
            val predicatesMap = mutableMapOf<String, (Any) -> Boolean>()
            val filteredCodebook = Codebook(codebook.variables
                                                    .filter { variable -> variable.key !in references.map { it[0] } })
            CodebookToPredicatesTransformer(filteredCodebook).predicates.forEach { (t, u) -> predicatesMap[t] = u }
            ReferencesToPredicatesTransformer(references).predicates.forEach { (t, u) -> predicatesMap[t] = u }

            val (database, predicates) = processor.createPredicatesFromData(predicatesMap)

            File(defaultDataoutputFolder).mkdirs()
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
