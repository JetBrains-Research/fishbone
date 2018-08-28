package org.jetbrains.bio.rules

import junit.framework.TestCase
import org.jetbrains.bio.predicates.TruePredicate
import org.jetbrains.bio.predicates.UndefinedPredicate
import java.util.stream.IntStream

class RuleTest : TestCase() {

    private fun rule(size: Int,
                     conditionSupport: Int,
                     targetSupport: Int,
                     conditionAndTarget: Int): Rule<Any> {
        return Rule(TruePredicate(), TruePredicate(),
                size, conditionSupport, targetSupport, conditionAndTarget)
    }

    @Throws(Exception::class)
    fun testConvictionFinite() {
        val rule1 = rule(10000, 6, 3777, 6)
        assertTrue(java.lang.Double.isFinite(rule1.conviction))
    }

    @Throws(Exception::class)
    fun testConvictionCondition() {
        assertTrue(rule(100, 90, 10, 10).conviction > rule(100, 100, 10, 10).conviction)

        // In case of small databases this is false
        assertFalse(rule(3, 2, 1, 1).conviction > rule(3, 3, 1, 1).conviction)
    }

    fun testType1Errors() {
        // NOTE: BF is not monotone
        testIncreasing(10000, 90, 0, 0, 0, 10, 10)
        testIncreasing(10000, 900, 0, 0, 0, 100, 100)
        testIncreasing(10000, 900, 0, 0, 0, 300, 300)
        testIncreasing(10000, 9000, 0, 0, 0, 1000, 1000)
        testIncreasing(10000, 9000, 0, 0, 0, 3000, 3000)
    }

    fun testType1AndType2Errors() {
        testIncreasing(10000, 90, 90, 0, 0, 0, 10)
        testIncreasing(10000, 900, 900, 0, 0, 0, 100)
        testIncreasing(10000, 900, 900, 0, 0, 0, 300)
        testIncreasing(10000, 9000, 9000, 0, 0, 0, 1000)
        testIncreasing(10000, 9000, 9000, 0, 0, 0, 3000)
    }

    private fun testIncreasing(dataSize: Int,
                               condition: Int,
                               target: Int,
                               support: Int,
                               deltaCondition: Int,
                               deltaTarget: Int,
                               deltaConditionAndTarget: Int) {
        var c = condition
        var t = target
        var s = support
        var prevScore = java.lang.Double.NEGATIVE_INFINITY
        while (s <= c && s <= t) {
            val score = rule(dataSize, c, t, s).conviction
            val delta = score - prevScore
            assert(delta > 0) {
                listOf(dataSize, c, t, s)
                        .map(Int::toString).joinToString(", ")
            }
            prevScore = score
            c += deltaCondition
            t += deltaTarget
            s += deltaConditionAndTarget
        }
    }

    fun testErrorType1() {
        // We use double ce value, because in case of integer
        // [20;30) => [20;50): ce = 1
        // [20;30) OR [30;40) => [20;50): ce = 2
        // and result values are SAME due to rounding issues
        IntStream.of(100, 1000, 10000).forEach { size ->
            assertTrue(rule(size, 10, 30, 10).conviction <
                    rule(size, 20, 30, 20).conviction)
            assertTrue(rule(size, 20, 30, 20).conviction <
                    rule(size, 30, 30, 30).conviction)
        }
    }

    @Throws(Exception::class)
    fun testInvalid() {
        try {
            val rule = rule(9998, 473, 473, 9525)
            assertTrue(java.lang.Double.isFinite(rule.conviction))
            fail()
        } catch (e: IllegalStateException) {
            // Do NOT fail
        }
    }

    @Throws(Exception::class)
    fun testCorrelationMinus1() {
        val rule = Rule(UndefinedPredicate<Any>(), UndefinedPredicate(), 20, 10, 10, 0)
        assertEquals(-1.0, rule.correlation, 1e-2)
    }

    @Throws(Exception::class)
    fun testCorrelationNaN() {
        val rule = Rule(UndefinedPredicate<Any>(), UndefinedPredicate(), 10, 10, 0, 0)
        assertTrue(rule.correlation.isNaN())
    }

    @Throws(Exception::class)
    fun testCorrelationPlus1() {
        val rule = Rule(UndefinedPredicate<Any>(), UndefinedPredicate(), 18, 10, 10, 10)
        assertEquals(1.0, rule.correlation, 1e-2)
    }

    @Throws(Exception::class)
    fun testCorrelationSymmetry() {
        val rule12 = Rule(UndefinedPredicate<Any>(), UndefinedPredicate(), 20, 10, 15, 10)
        val rule21 = Rule(UndefinedPredicate<Any>(), UndefinedPredicate(), 20, 15, 10, 10)
        assertEquals(rule12.correlation, rule21.correlation, 1e-2)
    }
}