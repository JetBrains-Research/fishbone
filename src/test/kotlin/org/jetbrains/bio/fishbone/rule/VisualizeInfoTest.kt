package org.jetbrains.bio.fishbone.rule

import org.jetbrains.bio.fishbone.predicate.Predicate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author Oleg Shpynov
 * @date 2019-07-16
 */

class VisualizeInfoTest {
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
        // Two alternatives are possible in this test due to parallel execution
        val expected = listOf(
                "Upset(names=[target, p2, p1], data=[0:2, 0,1:1, 1:3, 1,2:2])",
                "Upset(names=[target, p2, p1], data=[0:2, 0,1:1, 1:3, 2:2])"
        )
        assertTrue(expected.any {  it == upset.toString() })
    }


    @Test
    fun testHeatMap() {
        val predicates = predicates(3)
        val map = HeatMap.of((0..3).toList(), predicates + listOf(object : Predicate<Int>() {
            override fun test(item: Int) = item >= 2
            override fun name() = "target"
        }))
        assertEquals("[{key=p0, values=[{key=p0, value=1.0}, {key=p1, value=0.5773502691896258}, {key=p2, value=0.3333333333333333}, {key=target, value=-0.5773502691896258}]}, {key=p1, values=[{key=p0, value=0.5773502691896258}, {key=p1, value=1.0}, {key=p2, value=0.5773502691896258}, {key=target, value=-1.0}]}, {key=p2, values=[{key=p0, value=0.3333333333333333}, {key=p1, value=0.5773502691896258}, {key=p2, value=1.0}, {key=target, value=-0.5773502691896258}]}, {key=target, values=[{key=p0, value=-0.5773502691896258}, {key=p1, value=-1.0}, {key=p2, value=-0.5773502691896258}, {key=target, value=1.0}]}]",
                map.tableData.toString())
        assertEquals("{totalLength=1.9526542071168778, children=[{length=1.054937225671509, children=[{length=0.1675479172666882, children=[{length=0.43016906417868067, key=p0}, {length=0.43016906417868067, key=p1}]}, {length=0.6977169814453689, key=p2}]}, {length=1.852654207116878, key=target}]}",
                map.rootData.toString())
    }
}