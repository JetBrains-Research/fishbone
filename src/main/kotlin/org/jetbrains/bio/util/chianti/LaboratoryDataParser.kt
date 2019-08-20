package org.jetbrains.bio.util.chianti

import com.epam.parso.impl.SasFileReaderImpl
import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
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
        private val referenceFilename: String,
        private val sexReferenceFilename: String,
        private val dataFilename: String
) {

    fun readReferences(): List<CSVRecord> {
        val refReader = Files.newBufferedReader(referenceFilename.toPath())
        val references = CSVParser(refReader!!, CSVFormat.DEFAULT.withDelimiter(',')).map { csvRecord -> csvRecord }
        val sexRefReader = Files.newBufferedReader(sexReferenceFilename.toPath())
        val sexReferences = CSVParser(sexRefReader, CSVFormat.DEFAULT.withDelimiter(',')).map { csvRecord -> csvRecord }
        return references + sexReferences
    }

    fun createPredicatesFromData(predicateCodebooks: Map<String, (Any) -> Boolean>, references: List<CSVRecord>)
            : Pair<List<Int>, List<OverlapSamplePredicate>> {
        val sasFileReader = SasFileReaderImpl(FileInputStream(dataFilename))
        val data = sasFileReader.readAll()
        val dataByCode = data.map { it[0].toString().toLong() to it }.toMap()

        val columns = sasFileReader.columns.map { Regex("""^[XYZQC]_""").replaceFirst(it.name, "") }
        val indexedColumns = columns.withIndex()
        val sexColumnIdx = indexedColumns.first { it.value == "SEX" }.index
        val ageColumnIdx = indexedColumns.first { it.value == "AGEL" }.index
        val validFeatures = indexedColumns.filter { (idx, column) ->
            isEnoughPresentedFeature(data, ageColumnIdx, idx)
        }
        val columnsByIndex = validFeatures.map { it.index to it.value }.toMap()

        val predicates = mutableMapOf<String, List<Int>>()
        val sexDependentFeaturesValues = mutableMapOf<String, MutableMap<Long, MutableMap<Int, Double>>>()
        val combinedFeatures = mutableMapOf<Int, MutableMap<CombinedFeature, Map<String, Double>>>()
        val naValues = mutableMapOf<String, List<Int>>()

        val database = data.withIndex().map { (sampleIndex, sample) ->
            val code = sample[0].toString().toInt()
            println(sampleIndex)
            for ((columnIndex, column) in columnsByIndex) {
                val cell = sample[columnIndex]
                val sex = sample[sexColumnIdx] as Long
                if (cell != null) {
                    val cellValue = extractCellValue(cell)

                    val combinedFeature = CombinedFeature.getByLabel(column)
                    if (combinedFeature != null) {
                        val combinedFeatureValues = combinedFeatures.getOrDefault(code, mutableMapOf())
                        val fValues = combinedFeatureValues.getOrDefault(combinedFeature, emptyMap())
                        combinedFeatureValues[combinedFeature] = fValues + (column to cellValue.toString().toDouble())
                        combinedFeatures[code] = combinedFeatureValues

                        if (combinedFeature.title in SexDependentFeature.labels()) {
                            storeSexDependentFeaure(sexDependentFeaturesValues, combinedFeature.title, sex, cellValue, code)
                        }

                        continue
                    } else {
                        if (column in SexDependentFeature.labels()) {
                            storeSexDependentFeaure(sexDependentFeaturesValues, column, sex, cellValue, code)
                        } else {
                            predicateCodebooks
                                    .filter { (name, _) -> predicateNameIsApplicableForColumn(column, name) }
                                    .keys
                                    .forEach { p -> predicates[p] = predicates.getOrDefault(p, emptyList()) }
                            satisfiedPredicates(predicateCodebooks, column, cellValue).forEach { predicateName ->
                                predicates[predicateName] = (predicates.getOrDefault(predicateName, emptyList()) + code)
                            }
                        }
                    }
                } else {
                    storeNaValues(predicateCodebooks, column, naValues, code)
                }
            }
            code
        }.filter { code -> ageInRange(dataByCode.getValue(code.toLong())[5]) }

        combinedFeatures.forEach { (code, valuesMap) ->
            valuesMap.forEach { (feature, values) ->
                if (feature.title !in SexDependentFeature.labels()) {
                    val combinedValue = feature.combine(values)
                    satisfiedPredicates(predicateCodebooks, feature.title, combinedValue.toString()).forEach { predicate ->
                        predicates[predicate] = (predicates.getOrDefault(predicate, emptyList()) + code)
                    }
                }
                feature.labels.forEach { predicates.remove(it) }
            }
        }

        sexDependentFeaturesValues.forEach { (name, sexValues) ->
            processSexDependentFeature(sexValues, name, references, predicateCodebooks, predicates, combinedFeatures, data)
        }

        val overlapSamplePredicates = predicates.map {
            val notSamples = database.subtract(it.value + naValues.getOrDefault(it.key, emptyList())).toList()
            OverlapSamplePredicate(it.key, it.value, notSamples)
        }
        return Pair(database, overlapSamplePredicates)
    }

    fun isEnoughPresentedFeature(data: Array<Array<Any>>, ageColumnIndex: Int, idx: Int): Boolean {
        val youngData = data.filter { it[ageColumnIndex].toString().toInt() < 40 }
        val oldData = data.filter { it[ageColumnIndex].toString().toDouble() in oldAgeRange }
        return youngData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * youngData.size &&
                oldData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * oldData.size
    }

    private fun extractCellValue(cell: Any?): Serializable {
        return if (cell is Date)
            cell.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        else cell.toString()
    }

    private fun storeSexDependentFeaure(
            sexDependentFeaturesValues: MutableMap<String, MutableMap<Long, MutableMap<Int, Double>>>,
            column: String,
            sex: Long,
            cellValue: Any,
            code: Int
    ) {
        val sexValues = sexDependentFeaturesValues.getOrDefault(column, mutableMapOf(sex to mutableMapOf()))
        val valuesMap = sexValues.getOrDefault(sex, mutableMapOf())
        valuesMap[code] = cellValue.toString().toDouble()
        sexValues[sex] = valuesMap
        sexDependentFeaturesValues[column] = sexValues
    }

    private fun satisfiedPredicates(
            predicateCodebooks: Map<String, (Any) -> Boolean>, column: String, cellValue: Any
    ): Set<String> {
        return predicateCodebooks
                .filter { (name, predicate) ->
                    predicateNameIsApplicableForColumn(column, name) &&
                            !(column == "AGEL" && !(ageInRange(cellValue))) &&
                            !(column == "SEX" && cellValue.toString().toInt() == 1) &&
                            predicate(cellValue)
                }
                .keys
    }

    fun predicateNameIsApplicableForColumn(column: String, name: String): Boolean {
        return "(low|high|normal|above_ref|below_ref|inside_ref)_$column".toRegex().matches(name) ||
                (!"(low|high|normal|above_ref|below_ref|inside_ref).*".toRegex().matches(name)
                        && name.contains(column))
    }

    private fun ageInRange(cellValue: Any) =
            cellValue.toString().toDouble() in oldAgeRange || cellValue.toString().toDouble() < 40

    private fun storeNaValues(
            predicateCodebooks: Map<String, (Any) -> Boolean>,
            column: String,
            naValues: MutableMap<String, List<Int>>,
            code: Int
    ) {
        predicateCodebooks
                .filter { (name, _) -> predicateNameIsApplicableForColumn(column, name) }
                .forEach {
                    naValues[it.key] = naValues.getOrDefault(it.key, emptyList()) + code
                }
    }

    private fun processSexDependentFeature(
            sexValues: MutableMap<Long, MutableMap<Int, Double>>,
            name: String,
            references: List<CSVRecord>,
            predicateCodebooks: Map<String, (Any) -> Boolean>,
            dataPredicates: MutableMap<String, List<Int>>,
            combinedFeatures: MutableMap<Int, MutableMap<CombinedFeature, Map<String, Double>>>,
            data: Array<Array<Any>>
    ): List<Any> {
        return sexValues.map { (sexId, valuesMap) ->
            val sex = if (sexId == 1L) "male" else "female"

            val combinedFeature = CombinedFeature.getByTitle(name)
            val valuesMap2 = if (combinedFeature != null) {
                combinedFeatures
                        .filter { (code, _) -> data.find { it[0].toString().toInt() == code }!![2].toString().toLong() == sexId }
                        .filter { (_, vals) -> vals.containsKey(combinedFeature) }
                        .map { (code, vals) ->
                            code to combinedFeature.combine(vals.getValue(combinedFeature))
                        }.toMap().toMutableMap()
            } else valuesMap
            val name2 = combinedFeature?.title ?: name

            val isReferenceBasedFeature = "${name2}_$sex" in references.map { it[0] }
            if (isReferenceBasedFeature) {
                listOf("below_ref_$name2", "inside_ref_$name2", "above_ref_$name2").map { predicateName ->
                    val predicate = predicateCodebooks.getValue("${predicateName}_$sex")
                    dataPredicates[predicateName] = dataPredicates.getOrDefault(predicateName, emptyList())
                    valuesMap2.filter { (_, value) -> predicate(value.toString()) }.forEach { (code, _) ->
                        dataPredicates[predicateName] = (dataPredicates.getOrDefault(predicateName, emptyList()) + code)
                    }
                }
            } else {
                val q1 = valuesMap2.values.percentile(25.0)
                val q3 = valuesMap2.values.percentile(75.0)
                dataPredicates["low_$name2"] = dataPredicates.getOrDefault("low_$name2", emptyList())
                dataPredicates["high_$name2"] = dataPredicates.getOrDefault("low_$name2", emptyList())
                dataPredicates["normal_$name2"] = dataPredicates.getOrDefault("low_$name2", emptyList())
                valuesMap2.forEach { (code, value) ->
                    when {
                        value < q1 -> dataPredicates["low_$name2"] = (dataPredicates.getOrDefault("low_$name2", emptyList()) + code)
                        value > q3 -> dataPredicates["high_$name2"] = (dataPredicates.getOrDefault("high_$name2", emptyList()) + code)
                        else -> dataPredicates["normal_$name2"] = (dataPredicates.getOrDefault("normal_$name2", emptyList()) + code)
                    }
                }
            }
        }
    }

    companion object {
        private const val defaultDataOutputFolder =
                "/home/nina.lukashina/projects/fishbone_materials/chianti_data/experiments/exp14_death_with_age_female"
        val oldAgeRange: ClosedFloatingPointRange<Double> = 65.0..75.0

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
                val withReferencePredicates = ReferencesToPredicatesTransformer(references).predicates
                val deathInF1Predicate = mapOf("CODE98_is_dead" to { it: Any -> it.toString().toInt() in DeadInF1.values() })
                val predicatesMap = noReferencePredicates(codebook, references) + withReferencePredicates + deathInF1Predicate

                val (database, predicates) = processor.createPredicatesFromData(predicatesMap, references)

                File(defaultDataOutputFolder).mkdirs()
                saveDatabaseToFile(database)
                savePredicatesToFiles(predicates)
            }
        }

        fun noReferencePredicates(codebook: Codebook, references: List<CSVRecord>): Map<String, (Any) -> Boolean> {
            val noReferenceCodebook = Codebook(noReferenceVariables(codebook, references))
            return CodebookToPredicatesTransformer(noReferenceCodebook).predicates
        }

        private fun noReferenceVariables(codebook: Codebook, references: List<CSVRecord>) =
                codebook.variables.filter { variable ->
                    !references.map {
                        it[0].replace("_female", "").replace("_male", "")
                    }.any { it == variable.key }
                }

        private fun saveDatabaseToFile(database: List<Int>) {
            val databaseOutput = File("$defaultDataOutputFolder/database.txt")
            databaseOutput.createNewFile()
            databaseOutput.printWriter().use { out ->
                database.forEach { out.println(it) }
            }
        }

        private fun savePredicatesToFiles(predicates: List<OverlapSamplePredicate>) {
            predicates.map { predicate ->
                val output = File("$defaultDataOutputFolder/${predicate.name()}")
                output.printWriter().use { out ->
                    predicate.samples.forEach { out.println(it) }
                    predicate.notSamples.forEach { out.println("not: $it") }
                }
                output.absolutePath
            }
        }
    }
}
