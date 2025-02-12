package org.jetbrains.bio.fishbone.predicate

import org.jetbrains.bio.fishbone.rule.Rule
import org.junit.Assert
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


    @Test
    fun testAbf300Values() {
        // below are two real predicates from ABF300 dataset
        // first predicate is "donor is MALE", negation is "donor is FEMALE"
        // second predicate is "donors Creatinine > 1", negation is "donors Creatinine <= 1"
        // the values of confidence, coverage, lift and conviction
        // were calculated using `arules` R package
        //
        // rules                               support confidence  coverage     lift count conviction
        // 1 {Creatinine_gt_1} => {Gender_M} 0.4733333  0.9861111 0.4800000 1.183333    71  12.000000
        // 2 {Gender_M} => {Creatinine_gt_1} 0.4733333  0.5680000 0.8333333 1.183333    71   1.203704

        val database = (1..150).toList()
        val isMale = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125)
        val isFemale = listOf(126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150)
        val creatinine_gt_1 = listOf(4, 5, 6, 8, 9, 13, 14, 17, 18, 21, 23, 24, 25, 26, 27, 30, 32, 33, 34, 36, 39, 44, 46, 48, 49, 56, 57, 58, 60, 61, 62, 64, 65, 66, 67, 68, 69, 72, 74, 78, 80, 82, 84, 87, 88, 89, 92, 93, 95, 96, 97, 98, 99, 100, 102, 103, 104, 105, 106, 107, 108, 109, 110, 116, 117, 119, 120, 121, 122, 124, 125, 149)
        val creatinine_let_1 = listOf(1, 2, 3, 7, 10, 11, 12, 15, 16, 19, 20, 22, 28, 29, 31, 35, 37, 38, 40, 41, 42, 43, 45, 47, 50, 51, 52, 53, 54, 55, 59, 63, 70, 71, 73, 75, 76, 77, 79, 81, 83, 85, 86, 90, 91, 94, 101, 111, 112, 113, 114, 115, 118, 123, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 150)

        val malePredicate = OverlapSamplePredicate("is_male", isMale, isFemale)
        val creatinineHighPredicate = OverlapSamplePredicate("high_creatinine", creatinine_gt_1, creatinine_let_1)

        val rule1 = Rule(creatinineHighPredicate, malePredicate, database)
        assertEquals(71, rule1.intersection)
        Assert.assertEquals(1.183333, rule1.lift, 1e-4)
        Assert.assertEquals(12.000, rule1.conviction, 1e-2)
        Assert.assertNotEquals(12.000, rule1.conviction, 1e-6)
    }
}