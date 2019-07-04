package org.jetbrains.bio.rules

import com.google.common.collect.ObjectArrays
import com.google.gson.GsonBuilder
import org.apache.commons.csv.CSVFormat.DEFAULT
import org.apache.commons.csv.CSVRecord
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.predicates.PredicateParser
import org.jetbrains.bio.util.bufferedWriter
import org.jetbrains.bio.util.deleteIfExists
import org.jetbrains.bio.util.write
import java.awt.Color
import java.io.IOException
import java.nio.file.Path

/**
 * Class to log predicates or trees to csv and json suitable for rules_browser rendering
 */
class RulesLogger(val path: Path?, vararg params: String) {
    private val extraParams: Array<out String> = params

    companion object {
        private val LOG = Logger.getLogger(RulesLogger::class.java)
    }

    private val graphRecords = LinkedHashSet<Map<String, Any?>>()
    private val atomics = hashSetOf<String>()

    private val csvPrinter by lazy {
        DEFAULT.withCommentMarker('#')
                .withHeader(*ObjectArrays.concat(RuleRecord.PARAMS.toTypedArray(), extraParams, String::class.java))
                .print(path!!.bufferedWriter())
    }

    @Synchronized
    fun log(id: String, tree: List<RulesMiner.Node<*>>) {
        val visited = hashSetOf<String>()
        tree.forEach { n ->
            var node: RulesMiner.Node<*>? = n
            while (node != null) {
                val rule = node.rule
                val r = RuleRecord.fromRule(rule, id)
                if (r.conditionPredicate.name() in visited) {
                    break
                }
                visited.add(r.conditionPredicate.name())
                atomics.addAll(r.conditionPredicate.collectAtomics().map { it.name() })
                atomics.addAll(r.targetPredicate.collectAtomics().map { it.name() })
                // Log to csv
                log(r)
                // Json based visualization
                graphRecords.add(r.toMap() + mapOf(
                        "node" to node.element.name(),
                        "parent_node" to if (node.parent != null) node.parent!!.element.name() else null,
                        "parent_condition" to if (node.parent != null) node.parent!!.rule.conditionPredicate.name() else null,
                        "aux" to node.aux))
                node = node.parent
            }
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

    fun done(jsonPath: Path?, palette: (String) -> Color, criterion: String) {
        if (path != null) {
            csvPrinter.close()
        }
        if (jsonPath != null) {
            jsonPath.deleteIfExists()
            jsonPath.write(getJson(palette, criterion))
        }
    }

    private fun Color.toHex(): String = "#%02x%02x%02x".format(red, green, blue)

    fun getJson(palette: (String) -> Color, criterion: String = "conviction"): String {
        // We want null modification to map to "null"
        val json = mapOf(
                "records" to graphRecords.toList(),
                "palette" to atomics.associateBy({ it }, { palette(it).toHex() }),
                "criterion" to criterion
        )
        return GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(json)
    }

}

class RuleRecord<T>(val id: String, val conditionPredicate: Predicate<T>, val targetPredicate: Predicate<T>,
                    val database: Int, val condition: Int, val target: Int, val intersection: Int,
                    val support: Double, val confidence: Double,
                    val correlation: Double,
                    val lift: Double,
                    val conviction: Double,
                    val loe: Double,
                    val complexity: Int) {

    fun toCSV() = listOf(
            id,
            conditionPredicate.name(),
            targetPredicate.name(),
            database,
            condition,
            target,
            intersection,
            support,
            confidence,
            if (!correlation.isNaN()) correlation else 0.0,
            if (!lift.isNaN()) lift else 0.0,
            if (!conviction.isNaN()) conviction else 0.0,
            if (!loe.isNaN()) loe else 0.0,
            complexity)


    fun toMap() = PARAMS.zip(toCSV()).toMap()

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_CONDITION = "condition"
        private const val KEY_TARGET = "target"
        private const val KEY_DATABASE_COUNT = "database_count"
        private const val KEY_CONDITION_COUNT = "condition_count"
        private const val KEY_TARGET_COUNT = "target_count"
        private const val KEY_INTERSECTION_COUNT = "intersection_count"
        private const val KEY_SUPPORT = "support"
        private const val KEY_CONFIDENCE = "confidence"
        private const val KEY_CORRELATION = "correlation"
        private const val KEY_LIFT = "lift"
        private const val KEY_CONVICTION = "conviction"
        private const val KEY_LOE = "loe"
        private const val KEY_COMPLEXITY = "complexity"

        val PARAMS = listOf(
                KEY_ID,
                KEY_CONDITION,
                KEY_TARGET,
                KEY_DATABASE_COUNT,
                KEY_CONDITION_COUNT,
                KEY_TARGET_COUNT,
                KEY_INTERSECTION_COUNT,
                KEY_SUPPORT,
                KEY_CONFIDENCE,
                KEY_CORRELATION,
                KEY_LIFT,
                KEY_CONVICTION,
                KEY_LOE,
                KEY_COMPLEXITY)

        /**
         * These are consistent with [RuleRecord.toMap]
         */
        fun <T> fromRule(rule: Rule<T>, id: String = ""): RuleRecord<T> {
            with(rule) {
                return RuleRecord(id, conditionPredicate, targetPredicate,
                        database, condition, target, intersection,
                        condition.toDouble() / database, intersection.toDouble() / condition,
                        correlation, lift, conviction, loe,
                        conditionPredicate.complexity())
            }
        }

        fun <T> fromCSV(it: CSVRecord, function: (String) -> Predicate<T>): RuleRecord<T> {
            return RuleRecord(id = s(it, KEY_ID),
                    conditionPredicate = PredicateParser.parse(s(it, KEY_CONDITION), function)!!,
                    targetPredicate = PredicateParser.parse(s(it, KEY_TARGET), function)!!,
                    database = i(it, KEY_DATABASE_COUNT),
                    condition = i(it, KEY_CONDITION_COUNT), target = i(it, KEY_TARGET_COUNT),
                    intersection = i(it, KEY_INTERSECTION_COUNT),
                    support = d(it, KEY_SUPPORT), confidence = d(it, KEY_CONFIDENCE),
                    correlation = d(it, KEY_CORRELATION),
                    lift = d(it, KEY_LIFT),
                    conviction = d(it, KEY_CONVICTION),
                    loe = d(it, KEY_LOE),
                    complexity = i(it, KEY_COMPLEXITY))
        }

        private fun s(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s] else ""
        private fun d(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toDouble() else 0.0
        private fun i(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toInt() else 0

    }
}