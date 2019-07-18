package org.jetbrains.bio.util.chianti

import com.epam.parso.impl.SasFileReaderImpl
import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jetbrains.bio.predicates.OverlapSamplePredicate
import org.jetbrains.bio.util.chianti.model.*
import org.jetbrains.bio.util.parse
import org.jetbrains.bio.util.toPath
import org.nield.kotlinstatistics.percentile
import java.io.File
import java.io.FileInputStream
import java.io.Serializable
import java.nio.file.Files
import java.time.ZoneId
import java.util.*


/**
 * This class is used to parse labo_raw data from chianti dataset
 */
class LaboratoryDataParser(
        private val referenceFilename: String, private val sexReferenceFilename: String, private val dataFilename: String
) {

    fun readReferences(): List<CSVRecord> {
        val refReader = Files.newBufferedReader(referenceFilename.toPath())
        val references = CSVParser(refReader, CSVFormat.DEFAULT.withDelimiter(',')).map { csvRecord -> csvRecord }
        val sexRefReader = Files.newBufferedReader(sexReferenceFilename.toPath())
        val sexReferences = CSVParser(sexRefReader, CSVFormat.DEFAULT.withDelimiter(',')).map { csvRecord -> csvRecord }
        return references + sexReferences
    }

    fun createPredicatesFromData(predicateCodebooks: Map<String, (Any) -> Boolean>, references: List<CSVRecord>)
            : Pair<List<Int>, List<OverlapSamplePredicate>> {
        val sasFileReader = SasFileReaderImpl(FileInputStream(dataFilename))
        val data = sasFileReader.readAll()

        val ageColumnIndex = sasFileReader.columns.withIndex().first {
            Regex("""^[XYZQC]_AGEL""").matches(it.value.name)
        }.index
        val sexColumn = sasFileReader.columns.withIndex().first { it.value.name == "SEX" }.index

        val columnsByIndex = sasFileReader.columns.withIndex()
                .map { (idx, column) ->
                    idx to Regex("""^[XYZQC]_""").replaceFirst(column.name, "")
                }
                .filter { (idx, _) -> isEnoughPresentedFeature(data, ageColumnIndex, idx) }
                .toMap()

        val dataPredicates = mutableMapOf<String, List<Int>>()
        val sexDependentFeaturesValues = mutableMapOf<String, MutableMap<Long, List<Double>>>()
        val database = data.withIndex().map { (sampleIndex, sample) ->
            println(sampleIndex)
            for ((columnIndex, column) in columnsByIndex) {
                val cell = sample[columnIndex]
                val sex = sample[sexColumn] as Long
                if (cell != null) {
                    val cellValue = if (cell is Date)
                        cell.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    else cell.toString()

                    if (column in SexDependentFeature.labels()) {
                        storeSexDependentFeaure(sexDependentFeaturesValues, column, sex, cellValue)
                    } else {
                        val satisfiedPredicates = checkPredicatesOnFeature(predicateCodebooks, column, cell, cellValue)
                        satisfiedPredicates.forEach {
                            dataPredicates[it] = (dataPredicates.getOrDefault(it, emptyList()) + sampleIndex)
                        }
                    }
                }
            }
            sampleIndex
        }.filter { idx -> data[idx][5].toString().toDouble() in 65.0..75.0 || data[idx][5].toString().toDouble() < 40 }

        sexDependentFeaturesValues.forEach { (name, sexValues) ->
            processSexDependentFeature(sexValues, name, references, predicateCodebooks, dataPredicates)
        }

        return Pair(database, dataPredicates.map { OverlapSamplePredicate(it.key, it.value) })
    }

    private fun processSexDependentFeature(
            sexValues: MutableMap<Long, List<Double>>,
            name: String, references: List<CSVRecord>,
            predicateCodebooks: Map<String, (Any) -> Boolean>,
            dataPredicates: MutableMap<String, List<Int>>
    ): List<Any> {
        return sexValues.map { (sexId, values) ->
            val sex = if (sexId == 1L) "male" else "female"
            val isReferenceBasedFeature = "${name}_$sex" in references.map { it[0] }
            if (isReferenceBasedFeature) {
                listOf("below_ref_$name", "inside_ref_$name", "above_ref_$name").map {
                    val predicate = predicateCodebooks.getValue("${it}_$sex")
                    values.withIndex().filter { (_, value) -> predicate(value.toString()) }.forEach { (sampleIndex, _) ->
                        dataPredicates[it] = (dataPredicates.getOrDefault(it, emptyList()) + sampleIndex)
                    }
                }
            } else {
                val q1 = values.percentile(25.0)
                val q3 = values.percentile(75.0)
                values.withIndex().forEach { (sampleIndex, value) ->
                    when {
                        value < q1 -> dataPredicates["low_$name"] = (dataPredicates.getOrDefault("low_$name", emptyList()) + sampleIndex)
                        value > q3 -> dataPredicates["high_$name"] = (dataPredicates.getOrDefault("high_$name", emptyList()) + sampleIndex)
                        else -> dataPredicates["normal_$name"] = (dataPredicates.getOrDefault("normal_$name", emptyList()) + sampleIndex)
                    }
                }
            }
        }
    }

    private fun checkPredicatesOnFeature(
            predicateCodebooks: Map<String, (Any) -> Boolean>, column: String, cell: Any, cellValue: Any
    ): Set<String> {
        return predicateCodebooks
                .filter { (name, predicate) ->
                    ("(low|high|normal|above_ref|below_ref|inside_ref)_$column".toRegex().matches(name) ||
                            (!"(low|high|normal|above_ref|below_ref|inside_ref).*".toRegex().matches(name)
                                    && name.contains(column))
                            ) &&
                            !(column == "AGEL" && !(cell.toString().toDouble() in 65.0..75.0 ||
                                    cell.toString().toDouble() < 40)) &&
    //                                        !(column == "SEX" && cell.toString().toInt() == 2) &&
                            predicate(cellValue)
                }
                .keys
    }

    private fun storeSexDependentFeaure(
            sexDependentFeaturesValues: MutableMap<String, MutableMap<Long, List<Double>>>,
            column: String,
            sex: Long,
            cellValue: Any
    ) {
        val sexValues = sexDependentFeaturesValues.getOrDefault(
                column, mutableMapOf(sex to emptyList())
        )
        val values = sexValues.getOrDefault(sex, emptyList())
        sexValues[sex] = values + cellValue.toString().toDouble()
        sexDependentFeaturesValues[column] = sexValues
    }

    private fun isEnoughPresentedFeature(data: Array<Array<Any>>, ageColumnIndex: Int, idx: Int): Boolean {
        val youngData = data.filter { it[ageColumnIndex].toString().toInt() < 40 }
        val oldData = data.filter { it[ageColumnIndex].toString().toInt() in 65..75 }
        return youngData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * youngData.size &&
                oldData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * oldData.size
    }

    companion object {
        private const val defaultDataOutputFolder =
                "/home/nina.lukashina/projects/fishbone_materials/chianti_data/experiments/exp5_female"

        @JvmStatic
        fun main(args: Array<String>) {

            OptionParser().apply {
                accepts("dataFilename", "Filename of laboratory data file")
                        .withRequiredArg().ofType(String::class.java)
                accepts("codebookFilename", "Filename of laboratory codebook")
                        .withRequiredArg().ofType(String::class.java)
                accepts("referenceFilename", "Filename of laboratory variables references")
                        .withRequiredArg().ofType(String::class.java)
                accepts("sexReferenceFilename", "Filename of laboratory variables references for sex-dependent features")
                        .withRequiredArg().ofType(String::class.java)
                formatHelpWith(BuiltinHelpFormatter(200, 2))
            }.parse(args) { options ->
                val processor = LaboratoryDataParser(
                        options.valueOf("referenceFilename").toString(),
                        options.valueOf("sexReferenceFilename").toString(),
                        options.valueOf("dataFilename").toString()
                )
                val codebook = CodebookReader(options.valueOf("codebookFilename").toString()).readCodebook()
                val references = processor.readReferences()
                val predicatesMap = mutableMapOf<String, (Any) -> Boolean>()
                val withoutReferenceCodebook = Codebook(
                        codebook.variables.filter { variable ->
                            variable.key !in references.map { it[0] }
                        }
                )
                CodebookToPredicatesTransformer(withoutReferenceCodebook).predicates.forEach { (t, u) -> predicatesMap[t] = u }
                ReferencesToPredicatesTransformer(references).predicates.forEach { (t, u) -> predicatesMap[t] = u }

                val (database, predicates) = processor.createPredicatesFromData(predicatesMap, references)

                File(defaultDataOutputFolder).mkdirs()
                val databaseOutput = File("$defaultDataOutputFolder/database.txt")
                databaseOutput.createNewFile()
                databaseOutput.printWriter().use { out ->
                    database.forEach { out.println(it) }
                }
                val predicateFilenames = predicates.map { predicate ->
                    val output = File("$defaultDataOutputFolder/${predicate.name}")
                    output.printWriter().use { out ->
                        predicate.samples.forEach { out.println(it) }
                    }
                    output.absolutePath
                }

                predicateFilenames.forEach { println(it) }
            }
        }
    }
}
