package org.jetbrains.bio.fishbone.rule.visualize.upset

import com.google.common.collect.Comparators.lexicographical
import com.google.common.collect.ComparisonChain
import com.google.common.primitives.Ints
import org.apache.commons.math3.util.CombinatoricsUtils
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.util.await
import java.util.concurrent.Callable

/**
 * Data class describing joint combinations, inspired by UpsetR.
 * See [upset.js] for web-browser visualization of [Upset] converted to JSON.
 *
 * Reference:
 * Lex, Alexander, et al. "UpSet: visualization of intersecting sets."
 * IEEE transactions on visualization and computer graphics 20.12 (2014): 1983-1992.
 *
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
                    else -> {
                        @Suppress("UnstableApiUsage")
                        ComparisonChain.start()
                            .compare(u2.n, u1.n)
                            .compare(u1.id, u2.id, lexicographical { a, b -> Ints.compare(a, b) })
                            .result()
                    }
                }
            }
            val n = predicates.size + 1
            val cs = UpsetBoundedPriorityQueue(limit = combinations, comparator = comparator)
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