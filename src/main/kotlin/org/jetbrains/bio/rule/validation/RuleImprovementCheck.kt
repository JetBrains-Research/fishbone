package org.jetbrains.bio.rule.validation

import org.jetbrains.bio.miner.FishboneMiner
import org.jetbrains.bio.predicate.AndPredicate
import org.jetbrains.bio.predicate.OrPredicate
import org.jetbrains.bio.predicate.Predicate
import org.jetbrains.bio.rule.Rule
import org.jetbrains.bio.rule.validation.adjustment.BenjaminiHochbergAdjustment
import org.jetbrains.bio.rule.validation.adjustment.NoAdjustment
import org.slf4j.LoggerFactory

class RuleImprovementCheck {
    companion object {

        private const val SMALL_DATABASE_SIZE_THRESHOLD = 100
        private val logger = LoggerFactory.getLogger(RuleImprovementCheck::class.java)

        fun <T> productiveRules(
                rules: List<FishboneMiner.Node<T>>, alpha: Double, db: List<T>, adjust: Boolean
        ): List<FishboneMiner.Node<T>> {
            val pVals = rules.map { node -> node to testRuleProductivity(node.rule, db) }.sortedBy { it.second }
            val adjustment = if (adjust) BenjaminiHochbergAdjustment else NoAdjustment
            val multipleComparisonResults = adjustment.test(pVals, alpha, rules.size)
            val filteredRules = multipleComparisonResults
                    .filter { it.second }
                    .map { it.first }
            logger.info("Significant rules P < $alpha: ${filteredRules.size} / ${rules.size}")
            return filteredRules
        }

        /**
         * Returns pvalue for corresponding Null Hypothesis
         */
        fun <T> testRuleProductivity(rule: Rule<T>, database: List<T>, testName: String? = null): Double {
            val test = testName ?: (if (database.size > SMALL_DATABASE_SIZE_THRESHOLD) "chi" else "fisher")
            val sources = buildSources(rule)
            val target = rule.targetPredicate

            val a = aFreq(sources, target, database)
            val b = bFreq(sources, target, database)

            return sources.map { x ->
                val reducedSources = sources.filter { source -> source != x }
                val c = cFreq(reducedSources, x, target, database)
                val d = dFreq(reducedSources, x, target, database)

                SignificanceCheck.test(a, b, c, d, database.size, test)
            }.max()!!
        }

        private fun <T> buildSources(rule: Rule<T>): List<Predicate<T>> {
            return when (val conditionPredicate = rule.conditionPredicate) {
                is AndPredicate -> conditionPredicate.operands
                is OrPredicate -> {
                    val operands = conditionPredicate.operands
                    if (operands.size < 2) {
                        throw IllegalArgumentException("Operands size is less than 2") //TODO: refactor
                    }
                    val initial = operands[0].not()
                    listOf(operands.drop(1).map { it.not() }.fold(initial, { acc, p -> acc.and(p.not()) }).not())
                }
                else -> listOf(conditionPredicate)
            }
        }

        private fun <T> aFreq(sources: List<Predicate<T>>, target: Predicate<T>, database: List<T>) =
                AndPredicate(sources + target).test(database).cardinality()

        private fun <T> bFreq(sources: List<Predicate<T>>, target: Predicate<T>, database: List<T>) =
                AndPredicate(sources + target.not()).test(database).cardinality()

        private fun <T> cFreq(
                reducedSources: List<Predicate<T>>, x: Predicate<T>, target: Predicate<T>, database: List<T>
        ) = AndPredicate(reducedSources + x.not() + target).test(database).cardinality()

        private fun <T> dFreq(
                reducedSources: List<Predicate<T>>, x: Predicate<T>, target: Predicate<T>, database: List<T>
        ) = AndPredicate(reducedSources + x.not() + target.not()).test(database).cardinality()
    }

}