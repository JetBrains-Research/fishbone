package org.jetbrains.bio.fishbone.rule.validation

import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.predicate.AndPredicate
import org.jetbrains.bio.fishbone.predicate.OrPredicate
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.jetbrains.bio.fishbone.rule.validation.adjustment.BenjaminiHochbergAdjustment
import org.jetbrains.bio.fishbone.rule.validation.adjustment.NoAdjustment
import org.slf4j.LoggerFactory

/**
 * This class provides method to check rule's productivity in terms of 'improvement'.
 * {@see: https://www.tandfonline.com/doi/abs/10.1080/13658816.2018.1434525?journalCode=tgis20}
 */
class RuleImprovementCheck {
    companion object {

        private const val SMALL_DATABASE_SIZE_THRESHOLD = 100
        private val logger = LoggerFactory.getLogger(RuleImprovementCheck::class.java)

        /**
         * Filter out unproductive rules.
         *
         * @param rules rules to check
         * @param alpha significance level
         * @param db database
         * @param adjust if we want to use multiple comparison adjustment
         *
         * @return productve rules only
         */
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
         * Check is rule is productive
         *
         * @param rule rule to check
         * @param database database
         * @param testName test to use. Should be 'chi' or 'fisher'
         *
         * @return pvalue for corresponding Null Hypothesis
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

                StatisticalSignificanceCheck.test(a, b, c, d, database.size, test)
            }.maxOrNull()!!
        }

        /**
         * Obtain a list of rule sources. For OR-connected predicates rule is rewrited with AND connections.
         */
        private fun <T> buildSources(rule: Rule<T>): List<Predicate<T>> {
            return when (val conditionPredicate = rule.conditionPredicate) {
                is AndPredicate -> conditionPredicate.operands
                is OrPredicate -> {
                    val operands = conditionPredicate.operands
                    if (operands.size < 2) {
                        throw IllegalArgumentException("Operands size is less than 2")
                    }
                    val initial = operands[0].not()
                    listOf(operands.drop(1).map { it.not() }.fold(initial, { acc, p -> acc.and(p.not()) }).not())
                }
                else -> listOf(conditionPredicate)
            }
        }

        // Functions for frequency table

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