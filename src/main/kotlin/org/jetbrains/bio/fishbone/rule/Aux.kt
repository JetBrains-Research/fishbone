package org.jetbrains.bio.fishbone.rule

import org.apache.commons.math3.util.CombinatoricsUtils
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.util.await
import weka.clusterers.HackHierarchicalClusterer
import weka.core.Attribute
import weka.core.DenseInstance
import weka.core.EuclideanDistance
import weka.core.Instances
import java.util.*
import java.util.concurrent.Callable
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.collections.set
import kotlin.math.log2
import kotlin.math.roundToInt


/**
 * Auxiliary info for visualization purposes.
 */
interface Aux

data class RuleAux(val rule: Combinations) : Aux
data class TargetAux(val heatmap: HeatMap, val upset: Upset) : Aux


/**
 * Data class describing joint combinations.
 * @param names list of binary predicates names over given database
 * @param combinations combinations in binary bit-wise encoding order
 */
data class Combinations(val names: List<String>, val combinations: List<Int>) {
    companion object {
        fun <T> of(database: List<T>, predicates: List<Predicate<T>>): Combinations {
            check(predicates.size < log2(Int.MAX_VALUE.toDouble())) {
                "Maximum number of items which can be encoded as int exceeded ${predicates.size}"
            }
            val names = predicates.map { it.name() }
            val empiricalDistribution = EmpiricalDistribution(database, predicates)
            val combinations = (0 until (1 shl predicates.size)).map {
                (database.size * empiricalDistribution.probability(it)).roundToInt()
            }
            return Combinations(names, combinations)
        }
    }
}

data class UpsetRecord(val id: List<Int>, val n: Int) {
    override fun toString(): String {
        return "${id.joinToString(",") { it.toString() }}:$n"
    }
}

class UpsetBPQ(private val limit: Int,
               private val comparator: Comparator<UpsetRecord>,
               private val queue: Queue<UpsetRecord> = PriorityQueue(limit, comparator.reversed()))
    : Queue<UpsetRecord> by queue {

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

/**
 * Data class describing joint combinations, see UpsetR for details.
 * @param names list of binary predicates names over given database
 * @param data map : items -> intersection
 */
data class Upset(val names: List<String>, val data: List<UpsetRecord>) {
    companion object {
        fun <T> of(database: List<T>, predicates: List<Predicate<T>>, target: Predicate<T>,
                   combinations: Int = 100,
                   maxCombinations: Int = 100_000): Upset {
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
            // Will be reordered anyway by n
            val topCs = cs.sortedByDescending { it.n }.map { c ->
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

/**
 * Data class describing pairwise correlations cluster map
 * @param tableData data required for D3JS visualization
 * @param rootData dendrogram for clustering
 */
data class HeatMap(val tableData: List<Map<String, Any>>?, val rootData: Map<String, *>) {
    companion object {
        fun <T> of(database: List<T>, predicates: List<Predicate<T>>): HeatMap {
            // Instantiate clusterer
            val clusterer = HackHierarchicalClusterer()
            clusterer.options = arrayOf("-L", "COMPLETE")
            clusterer.debug = true
            clusterer.numClusters = 1
            clusterer.distanceFunction = EuclideanDistance()
            clusterer.distanceIsBranchLength = true

            // Build dataset
            val attributes = ArrayList(predicates.map {
                Attribute(it.name())
            })

            val data = Instances("Correlations", attributes, predicates.size)

            // Add data for clustering
            predicates.forEach { pI ->
                data.add(DenseInstance(1.0, predicates.map { pJ ->
                    Rule(pI, pJ, database).correlation
                }.toDoubleArray()))
            }

            // Cluster network
            clusterer.buildClusterer(data)

            // Clustering histogram
            val rootData = clusterer.getRootData(predicates.map { it.name() })

            // Now we should reorder predicates according to DFS in clustering histogram
            val order = clusterer.order()

            val tableData = order.map { i ->
                val pI = predicates[i]
                mapOf(
                        "key" to pI.name(),
                        "values" to order.map { j ->
                            val pJ = predicates[j]
                            mapOf(
                                    "key" to pJ.name(),
                                    "value" to Rule(pI, pJ, database).correlation)
                        })
            }
            return HeatMap(tableData, rootData)
        }
    }
}