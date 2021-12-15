package org.jetbrains.bio.fishbone.rule.visualize

import org.apache.commons.math3.util.CombinatoricsUtils
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.util.await
import java.util.*
import java.util.concurrent.Callable
import kotlin.Comparator
import kotlin.collections.LinkedHashMap

/**
 * Data class describing joint combinations, see UpsetR for details.
 * @param names list of binary predicates names over given database
 * @param data map : items -> intersection
 */
data class Upset(val names: List<String>, val data: List<UpsetRecord>) {
    companion object {
        fun <T> of(
            database: List<T>, predicates: List<Predicate<T>>, target: Predicate<T>,
            combinations: Int = 100,
            maxCombinations: Int = 100_000
        ): Upset {
            // Elements containing 0 should be on the top!
            val comparator = Comparator<UpsetRecord> { u1, u2 ->
                return@Comparator when {
                    0 in u1.id && 0 !in u2.id -> -1
                    0 !in u1.id && 0 in u2.id -> 1
                    else -> -u1.n.compareTo(u2.n)
                }
            }
            val n = predicates.size + 1
            val cs = UpsetBPQ(limit = combinations, comparator = comparator)
            var sumK = 0L
            (1..n).flatMap { k ->
                if (sumK > maxCombinations) {
                    return@flatMap emptyList<Callable<Unit>>()
                }
                CombinatoricsUtils.combinationsIterator(n, k).asSequence().map { c ->
                    sumK += 1
                    Callable {
                        val result = Predicate.and(c.map { if (it == 0) target else predicates[it - 1] }).test(database)
                        if (!result.isEmpty) {
                            val upsetRecord = UpsetRecord(c.toList(), result.cardinality())
                            synchronized(cs) {
                                cs.add(upsetRecord)
                            }
                        }
                    }
                }.asIterable()
            }.await(parallel = true)

            // Reorder labels
            val reordering = LinkedHashMap<Int, Int>()
            val topLabels = arrayListOf<String>()
            val topCs = cs.sortedWith(comparator).map { c ->
                UpsetRecord(c.id.map {
                    if (it !in reordering) {
                        reordering[it] = reordering.size
                        topLabels.add(if (it == 0) target.name() else predicates[it - 1].name())
                    }
                    reordering[it]!!
                }.sorted(), c.n)
            }

            return Upset(topLabels, topCs)
        }
    }
}

data class UpsetRecord(val id: List<Int>, val n: Int) {
    override fun toString(): String {
        return "${id.joinToString(",") { it.toString() }}:$n"
    }
}

/**
 * Bounded priority queue for [UpsetRecord]
 */
class UpsetBPQ(
    private val limit: Int,
    private val comparator: java.util.Comparator<UpsetRecord>,
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