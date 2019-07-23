package org.jetbrains.bio.rules.validation

import junit.framework.TestCase
import org.jetbrains.bio.predicates.OverlapSamplePredicate
import org.jetbrains.bio.predicates.ProbePredicate
import org.jetbrains.bio.rules.FishboneMiner
import org.jetbrains.bio.rules.Rule
import org.junit.Test

class ChiSquaredCheckTest : TestCase() {

    private val statCheck = ChiSquaredCheck()

    @Test
    fun test() {
        val database = listOf(1, 2, 3, 4, 5)
        val a = OverlapSamplePredicate("A", listOf(1, 2, 3, 5))
        val b = OverlapSamplePredicate("B", listOf(1, 3))
        val target = OverlapSamplePredicate("T", listOf(1, 2, 3, 5))

        val trueRule = Rule(a, target, database)
        assertTrue(statCheck.testRule(trueRule, database) < 0.05)

        val falseRule = Rule(b, target, database)
        assertFalse(statCheck.testRule(falseRule, database) < 0.05)
    }

    @Test
    fun testRandom() {
        val database = (0.until(1000)).toList()
        val probes = (0.until(100)).map { ProbePredicate("probe_$it", database) }
        val target = ProbePredicate("target", database)
        val result = FishboneMiner.mine(
                probes,
                target,
                database,
                maxComplexity = 2,
                function = Rule<Int>::conviction
        ).first()
        assertFalse(statCheck.testRule(result.rule, database) < 0.05)
    }

}