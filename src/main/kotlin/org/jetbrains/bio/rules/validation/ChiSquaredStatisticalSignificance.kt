package org.jetbrains.bio.rules.validation

import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.AndPredicate
import org.jetbrains.bio.predicates.OrPredicate
import org.jetbrains.bio.rules.Rule

/**
 * This class implements Chi-squared statistical significance test (see: https://www.tandfonline.com/eprint/aMdSMrAGuEHsHWSzIuqm/full)
 * TODO: it's not clear if we can use this approach for conjunction
 */
class ChiSquaredStatisticalSignificance {

    companion object {

        private val LOG = Logger.getLogger(ChiSquaredStatisticalSignificance::class.java)
        private val chiSquaredDistribution = ChiSquaredDistribution(1.0)
        const val SIGNIFICANCE_LEVEL = 0.05

        /**
         * Returns pvalue for Null Hypothesis that Left and Right parts of the rule are independent.
         */
        fun <T> test(rule: Rule<T>, database: List<T>): Double {
            LOG.debug("Testing rule's significance: $rule")
            val conditionPredicate = rule.conditionPredicate
            val sources = when (conditionPredicate) {
                is AndPredicate -> conditionPredicate.operands
                is OrPredicate -> {
                    val operands = conditionPredicate.operands
                    if (operands.size < 2) {
                        throw IllegalArgumentException("Operands size is less than 2") //TODO: refactor
                    }
                    val initial = operands[0].not()
                    listOf(operands.drop(1).map { it.not() }.fold(initial, {acc, p -> acc.and(p.not())}).not())
                }
                else -> listOf(conditionPredicate)
            }
            val target = rule.targetPredicate

            val len = database.size
            val a = AndPredicate(sources + target).test(database).cardinality().toDouble() / len
            val b = AndPredicate(sources + target.not()).test(database).cardinality().toDouble() / len

            val p = sources.map { x ->
                val reducedSources = sources.filter { source -> source != x }
                val c = AndPredicate(reducedSources + x.not() + target).test(database).cardinality().toDouble() / len
                val d = AndPredicate(reducedSources + x.not() + target.not()).test(database).cardinality().toDouble() / len

                val chiStat = ((a * d - b * c) * (a + b + c + d)) / ((a + b) * (c + d) * (a + c) * (b + d))
                1.0 - chiSquaredDistribution.cumulativeProbability(chiStat)
            }.max()!!

            return p
        }
    }
}