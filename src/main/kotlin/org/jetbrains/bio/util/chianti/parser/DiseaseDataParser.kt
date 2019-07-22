package org.jetbrains.bio.util.chianti.parser

import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.jetbrains.bio.predicate.OverlapSamplePredicate
import org.jetbrains.bio.util.chianti.codebook.Codebook
import org.jetbrains.bio.util.chianti.codebook.CodebookToPredicatesTransformer
import org.jetbrains.bio.util.parse
import java.time.ZoneId
import java.util.*


/**
 * This class is used to parse disease_raw data from chianti dataset
 */
// TODO: add NA support
class DiseaseDataParser(dataFilename: String) : DataParser(
        defaultDataOutputFolder = "/path/to/folder/for/predicates",
        targetSexId = 1,
        prefix = Regex("^(AX|IX)"),
        dataFilename = dataFilename
) {

    fun createPredicatesFromData(
            predicateCodebooks: Map<String, (Any) -> Boolean>
    ): Pair<List<Int>, List<OverlapSamplePredicate>> {
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

                    val satisfiedPredicates = satisfiedPredicates(predicateCodebooks, column, cellValue)
                    satisfiedPredicates.forEach {
                        dataPredicates[it] = (dataPredicates.getOrDefault(it, emptyList()) + code)
                    }
                }
            }
            code
        }

        return Pair(database, dataPredicates.map {
            val notSamples = database.subtract(it.value).toList()
            OverlapSamplePredicate(it.key, it.value, notSamples)
        })
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            OptionParser().apply {
                accepts("data", "Baseline filename of diseases data file")
                        .withRequiredArg().ofType(String::class.java)
                accepts("codebookFilename", "Filename of diseases codebook")
                        .withRequiredArg().ofType(String::class.java)
                formatHelpWith(BuiltinHelpFormatter(200, 2))
            }.parse(args) { options ->
                val processor = DiseaseDataParser(options.valueOf("data").toString())
                val codebook = Codebook(
                        options.valueOf("codebookFilename").toString(),
                        processor.irrelevantFeatures,
                        processor.redundantFeatures
                )

                val predicatesMap = CodebookToPredicatesTransformer(codebook, emptyList()).predicates
                val (database, predicates) = processor.createPredicatesFromData(predicatesMap)

                processor.saveDatabaseToFile(database)
                processor.savePredicatesToFiles(predicates)
            }
        }
    }
}
