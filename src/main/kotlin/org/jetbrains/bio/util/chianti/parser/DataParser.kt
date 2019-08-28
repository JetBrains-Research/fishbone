package org.jetbrains.bio.util.chianti.parser

import com.epam.parso.impl.SasFileReaderImpl
import org.jetbrains.bio.predicate.OverlapSamplePredicate
import org.nield.kotlinstatistics.range.ClosedOpenRange
import org.nield.kotlinstatistics.range.until
import java.io.File
import java.io.FileInputStream

open class DataParser(
        private val defaultDataOutputFolder: String,
        val targetSexId: Int,
        private val prefix: Regex,
        dataFilename: String,
        irrelevantFeaturesFile: String = "/util/chianti/irrelevant_features",
        redundantFeaturesFile: String = "/util/chianti/redundant_features",
        sexDependentFeaturesFile: String = "/util/chianti/sex_dependent_features",
        private val altSexId: Int = if (targetSexId == 1) 2 else 1,
        private val youngAgeRange: ClosedOpenRange<Double> = 0.0 until 40.0,
        private val oldAgeRange: ClosedFloatingPointRange<Double> = 65.0..75.0
) {

    internal val irrelevantFeatures: Set<String> = javaClass.getResource(irrelevantFeaturesFile).readText().split("\n").toSet()
    internal val redundantFeatures = javaClass.getResource(redundantFeaturesFile).readText().split("\n").toSet()
    internal val sexDependentFeatures = javaClass.getResource(sexDependentFeaturesFile).readText().split("\n").toSet()
    internal val data: Array<Array<Any>>
    internal val columns: List<String>
    internal val columnsByIndex: Map<Int, String>

    init {
        File(defaultDataOutputFolder).mkdirs()
        val sasFileReader = SasFileReaderImpl(FileInputStream(dataFilename))
        data = sasFileReader.readAll()
        columns = sasFileReader.columns.map { prefix.replaceFirst(it.name, "") }
        columnsByIndex = columnsByIndex()
    }

    private fun columnsByIndex(): Map<Int, String> {
        val indexedColumns = columns.withIndex()
        val ageColumnIdx = indexedColumns.first { it.value == AGE_COLUMN }.index
        val validFeatures = indexedColumns.filter { (idx, _) -> isEnoughPresentedFeature(data, ageColumnIdx, idx) }
        return validFeatures.map { it.index to it.value }.toMap()
    }

    fun isEnoughPresentedFeature(data: Array<Array<Any>>, ageColumnIndex: Int, idx: Int): Boolean {
        val youngData = data.filter { it[ageColumnIndex].toString().toDouble() in youngAgeRange }
        val oldData = data.filter { it[ageColumnIndex].toString().toDouble() in oldAgeRange }
        return youngData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * youngData.size &&
                oldData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * oldData.size
    }

    protected fun isValidAge(sample: Array<Any>, dataByCode: Map<Long, Array<Any>>): Boolean {
        val code = sample[0].toString().toLong()
        return ageInRange(dataByCode.getValue(code)[5])
    }

    protected fun isValidSex(sample: Array<Any>, dataByCode: Map<Long, Array<Any>>, sexColumnIdx: Int): Boolean {
        val code = sample[0].toString().toLong()
        return dataByCode.getValue(code)[sexColumnIdx].toString().toInt() == targetSexId
    }

    fun ageInRange(cellValue: Any) =
            cellValue.toString().toDouble() in oldAgeRange || cellValue.toString().toDouble() in youngAgeRange

    protected fun satisfiedPredicates(
            predicateCodebooks: Map<String, (Any) -> Boolean>, column: String, cellValue: Any
    ): Set<String> {
        return predicateCodebooks
                .filter { (name, predicate) ->
                    predicateNameIsApplicableForColumn(column, name) &&
                            !(column == AGE_COLUMN && !(ageInRange(cellValue))) &&
                            !(column == SEX_COLUMN && cellValue.toString().toInt() == altSexId) &&
                            predicate(cellValue)
                }
                .keys
    }

    protected fun saveDatabaseToFile(database: List<Int>) {
        val databaseOutput = File("$defaultDataOutputFolder/database.txt")
        databaseOutput.createNewFile()
        databaseOutput.printWriter().use { out ->
            database.forEach { out.println(it) }
        }
    }

    protected fun savePredicatesToFiles(predicates: List<OverlapSamplePredicate>) {
        predicates.map { predicate ->
            val output = File("$defaultDataOutputFolder/${predicate.name()}")
            output.printWriter().use { out ->
                predicate.samples.forEach { out.println(it) }
                predicate.notSamples.forEach { out.println("not: $it") }
            }
            output.absolutePath
        }
    }

    companion object {

        const val AGE_COLUMN = "AGEL"
        const val SEX_COLUMN = "SEX"

        fun predicateNameIsApplicableForColumn(column: String, name: String): Boolean {
            if (column.contains(AGE_COLUMN) || column.contains(SEX_COLUMN)) {
                return "(low|high|normal|above_ref|below_ref|inside_ref)_$column".toRegex().matches(name) ||
                        "(low|high|normal|above_ref|below_ref|inside_ref)_${column}_(male|female)".toRegex().matches(name) ||
                        (!"(low|high|normal|above_ref|below_ref|inside_ref).*".toRegex().matches(name)
                                && name.contains(column))
            }
            return "(low|high|normal|above_ref|below_ref|inside_ref)_$column".toRegex().matches(name) ||
                    "(low|high|normal|above_ref|below_ref|inside_ref)_${column}_(male|female)".toRegex().matches(name) ||
                    (!"(low|high|normal|above_ref|below_ref|inside_ref).*".toRegex().matches(name)
                            && name == column)
        }

    }

}

