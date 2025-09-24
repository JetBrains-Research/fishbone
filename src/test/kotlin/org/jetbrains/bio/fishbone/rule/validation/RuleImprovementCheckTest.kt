package org.jetbrains.bio.fishbone.rule.validation

import junit.framework.TestCase
import org.jetbrains.bio.statistics.hypothesis.Alternative
import org.jetbrains.bio.statistics.hypothesis.FisherExactTest
import org.junit.Test

class RuleImprovementCheckTest : TestCase() {
    @Test
    fun testFisherExactTest() {
        val a = 71
        val b = 54
        val c = 1
        val d = 24

        val test = FisherExactTest.forTable(a, b, c, d)
        assertEquals(3.322e-07, test.invoke(alternative = Alternative.TWO_SIDED).toDouble(), 1e-9)
    }

//    fun testChi() {
//        val database = listOf(1, 2, 3, 4, 5)
//        val a = OverlapSamplePredicate("A", listOf(1, 2, 3, 5), listOf(4))
//        val b = OverlapSamplePredicate("B", listOf(1, 3), listOf(2, 4, 5))
//        val target = OverlapSamplePredicate("T", listOf(1, 2, 3, 5), listOf(4))
//
//        val trueRule = Rule(a, target, database)
//        assertTrue(RuleImprovementCheck.testRuleProductivity(trueRule, database, "chi") < 0.05)
//
//        val falseRule = Rule(b, target, database)
//        assertFalse(RuleImprovementCheck.testRuleProductivity(falseRule, database, "chi") < 0.05)
//    }
}