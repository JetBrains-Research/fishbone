package org.jetbrains.bio.rules.validation

import org.jetbrains.bio.rules.Rule
import org.jetbrains.bio.statistics.hypothesis.FisherExactTest

/**
 * This class implements Fisher's exact statistical significance test
 * (see: http://users.monash.edu/~webb/Files/dsp.pdf)
 */
internal class FisherExactCheck : RuleSignificanceCheck() {

    override fun <T> testRule(rule: Rule<T>, database: List<T>): Double {
        val sources = buildSources(rule)
        val target = rule.targetPredicate

        val a = aFreq(sources, target, database)
        val b = bFreq(sources, target, database)

        return sources.map { x ->
            val reducedSources = sources.filter { source -> source != x }
            val c = cFreq(reducedSources, x, target, database)
            val d = dFreq(reducedSources, x, target, database)

            FisherExactTest.forTable(a, b, c, d).invoke()
        }.max()!!
    }
}