package org.jetbrains.bio.fishbone.rule.visualize.upset

import java.util.*

/**
 * Bounded priority queue for [UpsetRecord]
 */
class UpsetBoundedPriorityQueue(
    private val limit: Int,
    private val comparator: Comparator<UpsetRecord>,
    private val queue: Queue<UpsetRecord> = PriorityQueue(limit, comparator.reversed())
) : Queue<UpsetRecord> by queue {

    override fun add(element: UpsetRecord): Boolean = offer(element)

    override fun offer(element: UpsetRecord): Boolean {
        // NOTE[shpynov] main difference with [PriorityQueue]!!!
        if (size >= limit) {
            val head = peek()
            if (comparator.compare(element, head) > -1) {
                return false
            }
        }
        if (size >= limit) {
            poll()
        }
        return queue.offer(element)
    }
}