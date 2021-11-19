package org.jetbrains.bio.fishbone.rule.validation

import junit.framework.TestCase
import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.predicate.OverlapSamplePredicate
import org.jetbrains.bio.fishbone.predicate.ProbePredicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.junit.Test

class RuleImprovementCheckTest : TestCase() {
    @Test
    fun testRandomFisher() {
        val database = (0.until(100)).toList()
        val probes = (0.until(10)).map { ProbePredicate("probe_$it", database) }
        val target = ProbePredicate("target", database)
        val result = FishboneMiner.mine(
            probes,
            target,
            database,
            maxComplexity = 2,
            function = Rule<Int>::conviction
        ).first()
        assertFalse(RuleImprovementCheck.testRuleProductivity(result.rule, database, "fisher") < 0.05)
    }

    @Test
    fun testChi() {
        val database = listOf(1, 2, 3, 4, 5)
        val a = OverlapSamplePredicate("A", listOf(1, 2, 3, 5), listOf(4))
        val b = OverlapSamplePredicate("B", listOf(1, 3), listOf(2, 4, 5))
        val target = OverlapSamplePredicate("T", listOf(1, 2, 3, 5), listOf(4))

        val trueRule = Rule(a, target, database)
        assertTrue(RuleImprovementCheck.testRuleProductivity(trueRule, database, "chi") < 0.05)

        val falseRule = Rule(b, target, database)
        assertFalse(RuleImprovementCheck.testRuleProductivity(falseRule, database, "chi") < 0.05)
    }

    @Test
    fun testRandomChi() {
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
        assertFalse(RuleImprovementCheck.testRuleProductivity(result.rule, database, "chi") < 0.05)
    }
}