package org.jetbrains.bio.rules

import org.jetbrains.bio.predicates.Predicate
import org.junit.Test
import kotlin.test.assertEquals

/**
 * @author Oleg Shpynov
 * @date 2019-07-16
 */

class AuxTest {
    private fun predicates(number: Int): List<Predicate<Int>> {
        return 0.until(number).map {
            object : Predicate<Int>() {
                override fun test(item: Int): Boolean = item <= it

                override fun name(): String = "p$it"
            }
        }
    }

    @Test
    fun testCombination() {
        val predicates = predicates(3)
        val combinations = Combinations.of((0..2).toList(), predicates)
        assertEquals("Combinations(names=[p0, p1, p2], combinations=[0, 0, 0, 0, 1, 0, 1, 1])", combinations.toString())
    }

    @Test
    fun testUpset() {
        val predicates = predicates(3)
        val upset = Upset.of((0..3).toList(), predicates, object : Predicate<Int>() {
            override fun test(item: Int) = item >= 2

            override fun name() = "target"
        }, combinations = 4)
        assertEquals("Upset(names=[target, p0, p1, p2], data=[0:2, 0,3:1, 3:3, 2:2])", upset.toString())
    }

}