package org.jetbrains.bio.fishbone.rule.validation

import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.jetbrains.bio.statistics.hypothesis.FisherExactTest

/**
 * Provides method to check statistical significance with specified method.
 */
class StatisticalSignificanceCheck {

    /**
     * Class for Chi-squared test.
     * {@see: https://en.wikipedia.org/wiki/Chi-squared_test}
     */
    class ChiSquareTest(private val a: Double, private val b: Double, private val c: Double, private val d: Double) {
        fun invoke(): Double {
            val t1 = if (a + b != 0.0) a + b else 1.0
            val t2 = if (c + d != 0.0) c + d else 1.0
            val t3 = if (a + c != 0.0) a + c else 1.0
            val t4 = if (b + d != 0.0) b + d else 1.0
            val chiStat = ((a * d - b * c) * (a + b + c + d)) / (t1 * t2 * t3 * t4)
            val pValue =
                    if (chiStat.isInfinite() || chiStat.isNaN()) {
                        0.0
                    } else {
                        chiSquaredDistribution.cumulativeProbability(chiStat)
                    }
            return 1.0 - pValue
        }

        companion object {
            private val chiSquaredDistribution = ChiSquaredDistribution(1.0)

            fun forTable(a: Double, b: Double, c: Double, d: Double): ChiSquareTest = ChiSquareTest(a, b, c, d)
        }

    }

    companion object {

        /**
         * Test statistical significance according to frequency table and test name ('chi' or 'fisher')
         */
        fun test(a: Int, b: Int, c: Int, d: Int, len: Int, test: String): Double {
            return when (test) {
                "fisher" -> FisherExactTest.forTable(a, b, c, d).invoke()
                "chi" -> ChiSquareTest.forTable(
                        a.toDouble() / len, b.toDouble() / len, c.toDouble() / len, d.toDouble() / len
                ).invoke()
                else -> throw IllegalArgumentException("Unsupported test $test")
            }
        }
    }

}