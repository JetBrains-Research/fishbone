package org.jetbrains.bio.rules

import junit.framework.TestCase
import org.junit.Test

class BPQTest : TestCase() {

    @Test
    fun testBPQAddReplaceGreaterParent() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 9).named("1")
        val p2 = RangePredicate(9, 20).named("2")
        val p3 = RangePredicate(20, 40).named("3")
        val t = RangePredicate(0, 40).named("t")

        val queue = RM.BPQ(100, data, 0.0, 0.0)
        queue.add(RM.Node(Rule(p1.or(p2).or(p3), t, data), p2,
                RM.Node(Rule(p1.or(p3), t, data), p1, null)))
        assertEquals("[Node(rule=1 OR 2 OR 3 => t, element=2, parent=Node(rule=1 OR 3 => t, element=1, parent=null, aux=null), aux=null)]",
                queue.toList().toString())
        // Add same condition with greater parent, check replace
        queue.add(RM.Node(Rule(p1.or(p2).or(p3), t, data), p1,
                RM.Node(Rule(p2.or(p3), t, data), p2, null)))
        assertEquals("[Node(rule=1 OR 2 OR 3 => t, element=1, parent=Node(rule=2 OR 3 => t, element=2, parent=null, aux=null), aux=null)]",
                queue.toList().toString())
    }

    @Test
    fun testBPQAddWrongOrder() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 9).named("1")
        val p2 = RangePredicate(9, 20).named("2")
        val p3 = RangePredicate(20, 40).named("3")
        val t = RangePredicate(0, 40).named("t")

        val queue = RM.BPQ(100, data, 0.0, 0.0)
        // Nothing added because of wrong order
        queue.add(RM.Node(Rule(p1.or(p2), t, data), p2,
                RM.Node(Rule(p1, t, data), p1, null)))
        assertEquals("[]", queue.toList().toString())

        queue.add(RM.Node(Rule(p1.or(p2), t, data), p1,
                RM.Node(Rule(p2, t, data), p2, null)))
        assertEquals("[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null, aux=null), aux=null)]", queue.toList().toString())
    }


    @Test
    fun testBPQAddSameElement() {
        val data = 0.until(100).toList()
        val p1 = RangePredicate(0, 1).named("1")
        val p2 = RangePredicate(1, 9).named("2")
        val t = RangePredicate(0, 10).named("t")

        val queue = RM.BPQ(10, data, 0.0, 0.0)
        queue.add(RM.Node(Rule(p1.or(p2), t, data), p1, RM.Node(Rule(p2, t, data), p2, null)))
        assertEquals("[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null, aux=null), aux=null)]",
                queue.toList().toString())
        // Adding the same rules doesn't change the queue
        queue.add(RM.Node(Rule(p1.or(p2), t, data), p1, RM.Node(Rule(p2, t, data), p2, null)))
        assertEquals("[Node(rule=1 OR 2 => t, element=1, parent=Node(rule=2 => t, element=2, parent=null, aux=null), aux=null)]",
                queue.toList().toString())
    }

}