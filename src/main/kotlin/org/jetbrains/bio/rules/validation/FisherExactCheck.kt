package org.jetbrains.bio.rules.validation

import com.google.common.math.BigIntegerMath
import org.jetbrains.bio.rules.Rule
import kotlin.math.min

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

            (0..min(b, c)).map { i ->
                val t1 = BigIntegerMath.factorial(a + b) * BigIntegerMath.factorial(c + d) *
                        BigIntegerMath.factorial(a + c) * BigIntegerMath.factorial(b + d)
                val t2 = BigIntegerMath.factorial(a + b + c + d) * BigIntegerMath.factorial(a + i) *
                        BigIntegerMath.factorial(b - i) * BigIntegerMath.factorial(c - i) *
                        BigIntegerMath.factorial(d + i)
                t1.toDouble() / t2.toDouble()
            }.sum()
        }.max()!!
    }
}