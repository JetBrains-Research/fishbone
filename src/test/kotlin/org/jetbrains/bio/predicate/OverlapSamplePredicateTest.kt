package org.jetbrains.bio.predicate

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
}