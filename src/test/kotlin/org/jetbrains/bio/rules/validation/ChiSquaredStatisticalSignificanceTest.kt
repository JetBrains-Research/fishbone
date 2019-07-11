package org.jetbrains.bio.rules.validation

import junit.framework.TestCase
import org.jetbrains.bio.predicates.OverlapSamplePredicate
import org.jetbrains.bio.rules.Rule
import org.jetbrains.bio.rules.validation.ChiSquaredStatisticalSignificance.Companion.SIGNIFICANCE_LEVEL
import org.junit.Test

class ChiSquaredStatisticalSignificanceTest : TestCase() {

    @Test
    fun test() {
        val database = listOf(1, 2, 3, 4, 5)
        val a = OverlapSamplePredicate("A", listOf(1, 2, 3, 5))
        val b = OverlapSamplePredicate("B", listOf(1, 3))
        val target = OverlapSamplePredicate("T", listOf(1, 2, 3, 5))

        val trueRule = Rule(a, target, database)
        assertTrue(ChiSquaredStatisticalSignificance.test(trueRule, database) < SIGNIFICANCE_LEVEL)

        val falseRule = Rule(b, target, database)
        assertFalse(ChiSquaredStatisticalSignificance.test(falseRule, database) < SIGNIFICANCE_LEVEL)
    }

}