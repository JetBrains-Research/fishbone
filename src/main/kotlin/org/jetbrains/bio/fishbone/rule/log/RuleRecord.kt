package org.jetbrains.bio.fishbone.rule.log

import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.PredicateParser
import org.jetbrains.bio.fishbone.rule.Rule

class RuleRecord<T>(
    val id: String, val conditionPredicate: Predicate<T>, val targetPredicate: Predicate<T>,
    val database: Int, val condition: Int, val target: Int, val intersection: Int,
    val support: Double, val confidence: Double,
    val correlation: Double,
    val lift: Double,
    val conviction: Double,
    val loe: Double,
    val complexity: Int
) {

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
        complexity
    )


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
            KEY_COMPLEXITY
        )

        /**
         * These are consistent with [RuleRecord.toMap]
         */
        fun <T> fromRule(rule: Rule<T>, id: String = ""): RuleRecord<T> {
            with(rule) {
                return RuleRecord(
                    id, conditionPredicate, targetPredicate,
                    database, condition, target, intersection,
                    condition.toDouble() / database, intersection.toDouble() / condition,
                    correlation, lift, conviction, loe,
                    conditionPredicate.complexity()
                )
            }
        }

        fun <T> fromCSV(it: CSVRecord, factory: (String) -> Predicate<T>): RuleRecord<T> {
            return RuleRecord(
                id = s(it, KEY_ID),
                conditionPredicate = PredicateParser.parse(s(it, KEY_CONDITION), factory)!!,
                targetPredicate = PredicateParser.parse(s(it, KEY_TARGET), factory)!!,
                database = i(it, KEY_DATABASE_COUNT),
                condition = i(it, KEY_CONDITION_COUNT), target = i(it, KEY_TARGET_COUNT),
                intersection = i(it, KEY_INTERSECTION_COUNT),
                support = d(it, KEY_SUPPORT), confidence = d(it, KEY_CONFIDENCE),
                correlation = d(it, KEY_CORRELATION),
                lift = d(it, KEY_LIFT),
                conviction = d(it, KEY_CONVICTION),
                loe = d(it, KEY_LOE),
                complexity = i(it, KEY_COMPLEXITY)
            )
        }

        private fun s(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s] else ""
        private fun d(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toDouble() else 0.0
        private fun i(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toInt() else 0

    }
}