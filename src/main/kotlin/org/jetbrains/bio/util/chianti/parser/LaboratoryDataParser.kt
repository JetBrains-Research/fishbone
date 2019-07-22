package org.jetbrains.bio.util.chianti.parser

import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.predicate.OverlapSamplePredicate
import org.jetbrains.bio.util.chianti.codebook.Codebook
import org.jetbrains.bio.util.chianti.codebook.CodebookToPredicatesTransformer
import org.jetbrains.bio.util.chianti.variable.CombinedFeature
import org.jetbrains.bio.util.chianti.variable.Reference
import org.jetbrains.bio.util.parse
import org.jetbrains.bio.util.toPath
import org.nield.kotlinstatistics.percentile
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
        dataFilename: String
) : DataParser(
        defaultDataOutputFolder = "/path/to/folder/for/predicates",
        targetSexId = 1,
        prefix = Regex("""^[XYZQC]_"""),
        dataFilename = dataFilename
) {

    fun readReferences(): List<CSVRecord> {
        val refReader = Files.newBufferedReader(referenceFilename.toPath())
        val references = CSVParser(refReader!!, CSVFormat.DEFAULT.withDelimiter(',')).map { csvRecord -> csvRecord }
        val sexRefReader = Files.newBufferedReader(sexReferenceFilename.toPath())
        val sexReferences = CSVParser(sexRefReader, CSVFormat.DEFAULT.withDelimiter(',')).map { csvRecord -> csvRecord }
        return references + sexReferences
    }

    fun createPredicatesFromData(predicateCodebooks: Map<String, (Any) -> Boolean>, references: List<Reference>)
            : Pair<List<Int>, List<OverlapSamplePredicate>> {
        val dataByCode = data.map { it[0].toString().toLong() to it }.toMap()
        val sexColumnIdx = columns.withIndex().first { it.value == SEX_COLUMN }.index

        val predicates = mutableMapOf<String, List<Int>>()
        val sexDependentFeaturesValues = mutableMapOf<String, MutableMap<Long, MutableMap<Int, Double>>>()
        val combinedFeatures = mutableMapOf<Int, MutableMap<CombinedFeature, Map<String, Double>>>()
        val naValues = mutableMapOf<String, List<Int>>()

        val database = data.withIndex()
                .filter { (_, sample) -> isValidSex(sample, dataByCode, sexColumnIdx) }
                .filter { (_, sample) -> isValidAge(sample, dataByCode) }
                .map { (sampleIndex, sample) ->
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

                                if (combinedFeature.title in sexDependentFeatures) {
                                    storeSexDependentFeaure(sexDependentFeaturesValues, combinedFeature.title, sex, cellValue, code)
                                }

                                if (combinedFeature.preserveSources) {
                                    processOrdinaryColumn(
                                            column, sexDependentFeaturesValues, sex, cellValue, code, predicateCodebooks, predicates
                                    )
                                } else {
                                    continue
                                }
                            } else {
                                processOrdinaryColumn(
                                        column, sexDependentFeaturesValues, sex, cellValue, code, predicateCodebooks, predicates
                                )
                            }
                        } else {
                            storeNaValues(predicateCodebooks, column, naValues, code)
                        }
                    }
                    code
                }

        combinedFeatures.forEach { (code, valuesMap) ->
            processCombinedFeature(valuesMap, predicateCodebooks, predicates, code)
        }

        sexDependentFeaturesValues.forEach { (name, sexValues) ->
            processSexDependentFeature(sexValues, name, references, predicates, combinedFeatures, data)
        }

        return Pair(database, createOverlapSamplePredicates(predicates, naValues, database))
    }

    private fun createOverlapSamplePredicates(
            predicates: MutableMap<String, List<Int>>, naValues: MutableMap<String, List<Int>>, database: List<Int>
    ): List<OverlapSamplePredicate> {
        return predicates.map {
            val naSamples = naValues.getOrDefault(it.key, emptyList()) +
                    naValues.getOrDefault("${it.key}_male", emptyList()) +
                    naValues.getOrDefault("${it.key}_female", emptyList())
            val notSamples = database.subtract(it.value + naSamples).toList()
            OverlapSamplePredicate(it.key, it.value, notSamples)
        }
    }

    private fun processCombinedFeature(
            valuesMap: MutableMap<CombinedFeature, Map<String, Double>>,
            predicateCodebooks: Map<String, (Any) -> Boolean>,
            predicates: MutableMap<String, List<Int>>,
            code: Int
    ) {
        valuesMap.forEach { (feature, values) ->
            if (feature.title !in sexDependentFeatures) {
                val combinedValue = feature.combine(values)
                satisfiedPredicates(predicateCodebooks, feature.title, combinedValue.toString()).forEach { predicate ->
                    predicates[predicate] = (predicates.getOrDefault(predicate, emptyList()) + code)
                }
            }
            feature.labels.forEach { predicates.remove(it) }
        }
    }

    private fun processOrdinaryColumn(
            column: String,
            sexDependentFeaturesValues: MutableMap<String, MutableMap<Long, MutableMap<Int, Double>>>,
            sex: Long,
            cellValue: Serializable,
            code: Int,
            predicateCodebooks: Map<String, (Any) -> Boolean>,
            predicates: MutableMap<String, List<Int>>
    ) {
        if (column in sexDependentFeatures) {
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

    private fun extractCellValue(cell: Any?): Serializable {
        return if (cell is Date) cell.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() else cell.toString()
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
            references: List<Reference>,
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
                        .map { (code, vals) -> code to combinedFeature.combine(vals.getValue(combinedFeature)) }
                        .toMap()
                        .toMutableMap()
            } else valuesMap
            val name2 = combinedFeature?.title ?: name

            val isReferenceBasedFeature = "${name2}_$sex" in references.map { it.name }
            if (isReferenceBasedFeature) {
                references.find { it.name.contains("${name2}_$sex") }!!.getPredicates().map { p ->
                    val predicateName = p.key.replace("_female", "").replace("_male", "")
                    dataPredicates[predicateName] = dataPredicates.getOrDefault(predicateName, emptyList())
                    valuesMap2.filter { (_, value) -> p.value(value.toString()) }.forEach { (code, _) ->
                        dataPredicates[predicateName] = (dataPredicates.getOrDefault(predicateName, emptyList()) + code)
                    }
                }
            } else {
                val q1 = valuesMap2.values.percentile(25.0)
                val q3 = valuesMap2.values.percentile(75.0)
                listOf("low_$name2", "high_$name2", "normal_$name2").forEach {
                    dataPredicates[it] = dataPredicates.getOrDefault(it, emptyList())
                }
                valuesMap2.forEach { (code, value) ->
                    when {
                        value < q1 -> dataPredicates["low_$name2"] = (dataPredicates.getValue("low_$name2") + code)
                        value > q3 -> dataPredicates["high_$name2"] = (dataPredicates.getValue("high_$name2") + code)
                        else -> dataPredicates["normal_$name2"] = (dataPredicates.getValue("normal_$name2") + code)
                    }
                }
            }
        }
    }

    companion object {

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
                val codebook = Codebook(
                        options.valueOf("codebookFilename").toString(),
                        processor.irrelevantFeatures,
                        processor.redundantFeatures
                )
                val references = processor.readReferences()
                        .map { ref -> Reference(ref[0], ref[1].toDouble(), ref[3].toDouble()) }
                val predicatesMap = CodebookToPredicatesTransformer(codebook, references).predicates
                val (database, predicates) = processor.createPredicatesFromData(predicatesMap, references)

                processor.saveDatabaseToFile(database)
                processor.savePredicatesToFiles(predicates)
            }
        }
    }
}
