package org.jetbrains.bio.fishbone.rule.log

import com.google.common.collect.ObjectArrays
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.commons.csv.CSVFormat
import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.predicate.AndPredicate
import org.jetbrains.bio.fishbone.predicate.OrPredicate
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.PredicateParser
import org.jetbrains.bio.fishbone.rule.Rule
import org.jetbrains.bio.util.bufferedWriter
import org.jetbrains.bio.util.deleteIfExists
import org.jetbrains.bio.util.toPath
import org.jetbrains.bio.util.write
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.IOException
import java.nio.file.Path

/**
 * Class to log predicates or trees to csv and json suitable for rules_browser rendering
 */
class RulesLogger(val path: Path?, vararg params: String) {
    private val extraParams: Array<out String> = params

    private val graphRecords = LinkedHashSet<Map<String, Any?>>()
    private val atomics = hashSetOf<String>()

    private val csvPrinter by lazy {
        CSVFormat.DEFAULT.withCommentMarker('#')
            .withHeader(*ObjectArrays.concat(RuleRecord.PARAMS.toTypedArray(), extraParams, String::class.java))
            .print(path!!.bufferedWriter())
    }

    @Synchronized
    fun log(id: String, tree: List<FishboneMiner.Node<*>>) {
        val visited = hashSetOf<String>()
        tree.forEach { n ->
            var node: FishboneMiner.Node<*>? = n
            while (node != null) {
                val rule = node.rule
                val r = RuleRecord.fromRule(rule, id)
                val conditionPredicate = r.conditionPredicate
                if (conditionPredicate.name() in visited) {
                    break
                }
                visited.add(conditionPredicate.name())
                atomics.addAll(
                    conditionPredicate.collectAtomics().map {
                        // Hack OverlapSamplePredicate
                        it.name().replace("${PredicateParser.NOT.token} ", "")
                    }
                )
                atomics.addAll(r.targetPredicate.collectAtomics().map { it.name() })
                // Log to csv
                log(r)
                // Json based visualization
                graphRecords.add(
                    r.toMap() + mapOf(
                        "node" to node.element.name(),
                        "parent_node" to if (node.parent != null) node.parent!!.element.name() else null,
                        "parent_condition" to if (node.parent != null) node.parent!!.rule.conditionPredicate.name() else null,
                        "aux" to node.visualizeInfo,
                        "operator" to getOperatorName(conditionPredicate)
                    )
                )
                node = node.parent
            }
        }
    }

    private fun <T> getOperatorName(predicate: Predicate<T>): String {
        return when (predicate) {
            is AndPredicate<T> -> "and"
            is OrPredicate<T> -> "or"
            else -> "none"
        }
    }

    @Synchronized
    fun log(id: String, rule: Rule<*>, vararg info: String) {
        log(RuleRecord.fromRule(rule, id), *info)
    }

    @Synchronized
    fun log(record: RuleRecord<*>, vararg info: String) {
        if (path != null) {
            try {
                val printer = csvPrinter
                printer.printRecord(*(record.toCSV() + info).toTypedArray())
                printer.flush()
            } catch (e: IOException) {
                LOG.error(e.message, e)
            }
        }
    }

    fun save(criterion: String, palette: (String) -> Color) {
        if (path != null) {
            csvPrinter.close()
        }
        val jsonPath = path.toString().replace(".csv", ".json").toPath()
        jsonPath.deleteIfExists()
        jsonPath.write(GSON.toJson(prepareJson(criterion, palette)))
    }


    fun prepareJson(criterion: String, palette: (String) -> Color) = mapOf(
        "records" to graphRecords.toList(),
        "palette" to atomics.associateBy({ it }, { palette(it).toHex() }),
        "criterion" to criterion
    )

    companion object {
        private val LOG = LoggerFactory.getLogger(RulesLogger::class.java)

        val GSON: Gson = GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()

        private fun Color.toHex(): String = "#%02x%02x%02x".format(red, green, blue)
    }

}
