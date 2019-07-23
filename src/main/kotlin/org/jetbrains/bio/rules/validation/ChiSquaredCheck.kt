package org.jetbrains.bio.rules.validation

import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.jetbrains.bio.rules.Rule

/**
 * This class implements Chi-squared statistical significance test (see: https://www.tandfonline.com/eprint/aMdSMrAGuEHsHWSzIuqm/full)
 */
internal class ChiSquaredCheck : RuleSignificanceCheck() {

    private val chiSquaredDistribution = ChiSquaredDistribution(1.0)

    override fun <T> testRule(rule: Rule<T>, database: List<T>): Double {
        val sources = buildSources(rule)
        val target = rule.targetPredicate

        val len = database.size
        val a = aFreq(sources, target, database).toDouble() / len
        val b = bFreq(sources, target, database).toDouble() / len

        return sources.map { x ->
            val reducedSources = sources.filter { source -> source != x }
            val c = cFreq(reducedSources, x, target, database).toDouble() / len
            val d = dFreq(reducedSources, x, target, database).toDouble() / len

            val chiStat = ((a * d - b * c) * (a + b + c + d)) / ((a + b) * (c + d) * (a + c) * (b + d))
            1.0 - chiSquaredDistribution.cumulativeProbability(chiStat)
        }.max()!!
    }
}