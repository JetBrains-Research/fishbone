package org.jetbrains.bio.rules

import junit.framework.TestCase
import org.junit.Test

class BPQTest : TestCase() {

    @Test
    fun testBPQAdd() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 1).named("1")
        val p2 = RangePredicate(0, 3).named("2")
        val p3 = RangePredicate(0, 4).named("3")
        val t = RangePredicate(0, 10).named("t")

        val queue = RM.BPQ<Int>(1, RM.Node.comparator())
        queue.add(RM.Node(Rule(p1, t, data), p1, null))
        assertEquals("[Node(rule=1 => t, element=1, parent=null)]",
                queue.toList().toString())
        queue.add(RM.Node(Rule(p2.and(p3), t, data), p3, RM.Node(Rule(p2, t, data), p2, null)))
        assertEquals("[Node(rule=2 AND 3 => t, element=3, parent=Node(rule=2 => t, element=2, parent=null))]",
                queue.toList().toString())
    }

    @Test
    fun testBPQRemoveElement() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 1).named("1")
        val p2 = RangePredicate(1, 9).named("2")
        val t = RangePredicate(0, 10).named("t")

        val queue = RM.BPQ<Int>(10, RM.Node.comparator())
        queue.add(RM.Node(Rule(p1.or(p2), t, data), p2, RM.Node(Rule(p1, t, data), p1, null)))
        assertEquals("[Node(rule=1 OR 2 => t, element=2, parent=Node(rule=1 => t, element=1, parent=null))]",
                queue.toList().toString())
        // This node will overwrite previous one
        queue.add(RM.Node(Rule(p1.or(p2), t, data), p1, RM.Node(Rule(p2, t, data), p2, null)))
        assertEquals("[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null))]",
                queue.toList().toString())
    }

    @Test
    fun testBPQAddSameElement() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 1).named("1")
        val p2 = RangePredicate(1, 9).named("2")
        val t = RangePredicate(0, 10).named("t")

        val queue = RM.BPQ<Int>(10, RM.Node.comparator())
        queue.add(RM.Node(Rule(p1.or(p2), t, data), p1, RM.Node(Rule(p2, t, data), p2, null)))
        assertEquals("[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null))]",
                queue.toList().toString())
        // Adding the same rules doesn't change the queue
        queue.add(RM.Node(Rule(p1.or(p2), t, data), p1, RM.Node(Rule(p2, t, data), p2, null)))
        assertEquals("[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null))]",
                queue.toList().toString())
    }

}