package org.jetbrains.bio.util.chianti

import com.epam.parso.impl.SasFileReaderImpl
import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.jetbrains.bio.predicates.OverlapSamplePredicate
import org.jetbrains.bio.util.chianti.model.*
import org.jetbrains.bio.util.parse
import java.io.File
import java.io.FileInputStream
import java.time.ZoneId
import java.util.*


/**
 * This class is used to parse disease_raw data from chianti dataset
 */
class DiseaseDataParser(
        private val bl: String
) {

    fun createPredicatesFromData(predicateCodebooks: Map<String, (Any) -> Boolean>)
            : Pair<List<Int>, List<OverlapSamplePredicate>> {
        val sasFileReader = SasFileReaderImpl(FileInputStream(bl))
        val data = sasFileReader.readAll()

        val ageColumnIndex = sasFileReader.columns.withIndex().first { it.value.name == "IXAGE" }.index

        val columnsByIndex = sasFileReader.columns.withIndex()
                .map { (idx, column) ->
                    idx to Regex("^(AX|IX)").replaceFirst(column.name, "")
                }
                .filter { (idx, _) -> isEnoughPresentedFeature(data, ageColumnIndex, idx) }
                .toMap()

        val dataPredicates = mutableMapOf<String, List<Int>>()
        val database = data.withIndex().map { (_, sample) ->
            val code = sample[0].toString().toInt()
            println(code)
            for ((columnIndex, column) in columnsByIndex) {
                val cell = sample[columnIndex]
                if (cell != null) {
                    val cellValue = if (cell is Date)
                        cell.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    else cell.toString()

                    val satisfiedPredicates = checkPredicatesOnFeature(predicateCodebooks, column, cellValue)
                    satisfiedPredicates.forEach {
                        dataPredicates[it] = (dataPredicates.getOrDefault(it, emptyList()) + code)
                    }
                }
            }
            code
        }

        return Pair(database, dataPredicates.map { OverlapSamplePredicate(it.key, it.value) })
    }

    private fun checkPredicatesOnFeature(
            predicateCodebooks: Map<String, (Any) -> Boolean>, column: String, cellValue: Any
    ): Set<String> {
        return predicateCodebooks
                .filter { (name, predicate) ->
                    ("(low|high|normal)_$column".toRegex().matches(name) ||
                            (!"(low|high|normal).*".toRegex().matches(name) && name.contains(column))) &&
                            !(column == "AGE" && !(cellValue.toString().toDouble() in 65.0..75.0 ||
                                    cellValue.toString().toDouble() < 40)) &&
                            !(column == "SEX" && cellValue.toString().toInt() == 2) &&
                            predicate(cellValue)
                }
                .keys
    }

    private fun isEnoughPresentedFeature(data: Array<Array<Any>>, ageColumnIndex: Int, idx: Int): Boolean {
        val youngData = data.filter { it[ageColumnIndex].toString().toInt() < 40 }
        val oldData = data.filter { it[ageColumnIndex].toString().toInt() in 65..75 }
        return youngData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * youngData.size &&
                oldData.map { if (it[idx] != null) 1 else 0 }.sum() >= 0.8 * oldData.size
    }

    companion object {
        private const val defaultDataOutputFolder =
                "/path/to/folder/for/men/predicates"

        @JvmStatic
        fun main(args: Array<String>) {

            OptionParser().apply {
                accepts("data", "Baseline filename of diseases data file")
                        .withRequiredArg().ofType(String::class.java)
                accepts("codebookFilename", "Filename of diseases codebook")
                        .withRequiredArg().ofType(String::class.java)
                formatHelpWith(BuiltinHelpFormatter(200, 2))
            }.parse(args) { options ->
                val processor = DiseaseDataParser(
                        options.valueOf("data").toString()
                )
                val codebook = Codebook(CodebookReader(options.valueOf("codebookFilename").toString())
                                                .readCodebook().variables)
                val predicatesMap = mutableMapOf<String, (Any) -> Boolean>()

                CodebookToPredicatesTransformer(codebook).predicates.forEach { (t, u) -> predicatesMap[t] = u }

                val (database, predicates) = processor.createPredicatesFromData(predicatesMap)

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
