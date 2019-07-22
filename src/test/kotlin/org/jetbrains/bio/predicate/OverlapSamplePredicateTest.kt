package org.jetbrains.bio.predicate

import org.jetbrains.bio.rule.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlapSamplePredicateTest {

    @Test
    fun simpleTest() {
        val samples = listOf(1, 2, 3)
        val notSamples = listOf(4, 5)
        val p = OverlapSamplePredicate("test", samples, notSamples)

        assertEquals("test", p.name())
        samples.forEach { assertTrue(p.test(it)) }
        notSamples.forEach { assertFalse(p.test(it)) }
    }

    @Test
    fun testNot() {
        val samples = listOf(1, 2, 3)
        val notSamples = listOf(4, 5)
        val p = OverlapSamplePredicate("test", samples, notSamples).not()

        assertEquals("NOT test", p.name())
        notSamples.forEach { assertTrue(p.test(it)) }
        samples.forEach { assertFalse(p.test(it)) }
    }

    @Test
    fun testEquality() {
        val database = listOf(1, 2, 3, 4, 5)
        val a = OverlapSamplePredicate("A", listOf(1, 2, 3, 5), listOf(4))
        val b = OverlapSamplePredicate("B", listOf(1, 3), listOf(2, 4, 5))
        val target = OverlapSamplePredicate("T", listOf(1, 2, 3, 5), listOf(4))

        val rule1 = Rule(AndPredicate(listOf(a, b)), target, database)
        val rule2 = Rule(AndPredicate(listOf(a, b)), target, database)
        val rule3 = Rule(b, target, database)

        val rules = listOf(rule1, rule2, rule3)
        val distinctRules = rules.distinct()
        assertEquals(2, distinctRules.size)
        assertTrue(distinctRules.containsAll(listOf(rule1, rule3)))
    }
}