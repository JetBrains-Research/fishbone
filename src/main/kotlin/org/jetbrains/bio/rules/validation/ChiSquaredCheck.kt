package org.jetbrains.bio.rules.validation

import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.jetbrains.bio.rules.Rule

/**
 * This class implements Chi-squared statistical significance test
 * (see: https://www.tandfonline.com/eprint/aMdSMrAGuEHsHWSzIuqm/full)
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
            // TODO: what is a correct behavior when there is 0 in denominator?
            val t1 = if (a + b != 0.0) a + b else 1.0
            val t2 = if (c + d != 0.0) c + d else 1.0
            val t3 = if (a + c != 0.0) a + c else 1.0
            val t4 = if (b + d != 0.0) b + d else 1.0
            val chiStat = ((a * d - b * c) * (a + b + c + d)) / (t1 * t2 * t3 * t4)
            val stat =
                    if (chiStat.isInfinite() || chiStat.isNaN()) {
                        0.0
                    } else {
                        chiSquaredDistribution.cumulativeProbability(chiStat)
                    }
            1.0 - stat
        }.max()!!
    }
}