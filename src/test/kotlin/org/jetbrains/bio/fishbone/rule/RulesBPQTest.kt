package org.jetbrains.bio.fishbone.rule

import junit.framework.TestCase
import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.miner.RangePredicate
import org.jetbrains.bio.fishbone.miner.named
import org.junit.Test

class RulesBPQTest : TestCase() {

    @Test
    fun testBPQAddReplaceGreaterParent() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 9).named("1")
        val p2 = RangePredicate(9, 20).named("2")
        val p3 = RangePredicate(20, 40).named("3")
        val t = RangePredicate(0, 40).named("t")

        val queue = RulesBPQ(100, data, Rule<Int>::conviction, 0.0, 0.0)
        queue.add(
            FishboneMiner.Node(
                Rule(p1.or(p2).or(p3), t, data), p2,
                FishboneMiner.Node(Rule(p1.or(p3), t, data), p1, null)
            )
        )
        assertEquals(
            "[Node(rule=1 OR 2 OR 3 => t, element=2, parent=Node(rule=1 OR 3 => t, element=1, parent=null, visualizeInfo=null), visualizeInfo=null)]",
            queue.toList().toString()
        )
        // Add same condition with greater parent, check replace
        queue.add(
            FishboneMiner.Node(
                Rule(p1.or(p2).or(p3), t, data), p1,
                FishboneMiner.Node(Rule(p2.or(p3), t, data), p2, null)
            )
        )
        assertEquals(
            "[Node(rule=1 OR 2 OR 3 => t, element=1, parent=Node(rule=2 OR 3 => t, element=2, parent=null, visualizeInfo=null), visualizeInfo=null)]",
            queue.toList().toString()
        )
    }

    @Test
    fun testBPQAddWrongOrder() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 9).named("1")
        val p2 = RangePredicate(9, 20).named("2")
        val t = RangePredicate(0, 40).named("t")

        val queue = RulesBPQ(100, data, Rule<Int>::conviction, 0.0, 0.0)
        // Nothing added because of wrong order
        queue.add(
            FishboneMiner.Node(
                Rule(p1.or(p2), t, data), p2,
                FishboneMiner.Node(Rule(p1, t, data), p1, null)
            )
        )
        assertEquals("[]", queue.toList().toString())

        queue.add(
            FishboneMiner.Node(
                Rule(p1.or(p2), t, data), p1,
                FishboneMiner.Node(Rule(p2, t, data), p2, null)
            )
        )
        assertEquals(
            "[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null, visualizeInfo=null), visualizeInfo=null)]",
            queue.toList().toString()
        )
    }


    @Test
    fun testBPQAddSameElement() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 1).named("1")
        val p2 = RangePredicate(1, 9).named("2")
        val t = RangePredicate(0, 10).named("t")

        val queue = RulesBPQ(10, data, Rule<Int>::conviction, 0.0, 0.0)
        queue.add(FishboneMiner.Node(Rule(p1.or(p2), t, data), p1, FishboneMiner.Node(Rule(p2, t, data), p2, null)))
        assertEquals(
            "[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null, visualizeInfo=null), visualizeInfo=null)]",
            queue.toList().toString()
        )
        // Adding the same rules doesn't change the queue
        queue.add(FishboneMiner.Node(Rule(p1.or(p2), t, data), p1, FishboneMiner.Node(Rule(p2, t, data), p2, null)))
        assertEquals(
            "[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null, visualizeInfo=null), visualizeInfo=null)]",
            queue.toList().toString()
        )
    }

}