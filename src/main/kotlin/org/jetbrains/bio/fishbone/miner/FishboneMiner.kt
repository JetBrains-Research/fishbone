package org.jetbrains.bio.fishbone.miner

import com.google.common.annotations.VisibleForTesting
import org.jetbrains.bio.fishbone.miner.FishboneMiner.Node
import org.jetbrains.bio.fishbone.miner.FishboneMiner.mine
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.TruePredicate
import org.jetbrains.bio.fishbone.rule.*
import org.jetbrains.bio.util.MultitaskProgress
import org.jetbrains.bio.util.await
import org.jetbrains.bio.util.awaitAll
import org.jetbrains.bio.util.parallelismLevel
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.min


/**
 * Fishbone Associated Rule Mining (FARM) algorithm.
 * Used to mine hierarchical rules, combining both optimization metric and information gain.
 * Result is a set of [Node], which represents a single node of a graph.
 *
 * FARM is based on Beam search (heuristic search algorithm that explores a graph
 * by expanding the most promising node in a limited set).
 * Beam search space is realised by [RulesBPQ].
 * @see https://en.wikipedia.org/wiki/Beam_search
 */
object FishboneMiner : Miner {
    const val TOP_PER_COMPLEXITY = 100
    const val FUNCTION_DELTA = 1E-3
    const val KL_DELTA = 1E-3


    private val LOG = LoggerFactory.getLogger(FishboneMiner::class.java)

    /**
     * Result of [mine] procedure.
     */
    data class Node<T>(
        val rule: Rule<T>,
        val element: Predicate<T>,
        val parent: Node<T>?,
        var visualizeInfo: VisualizeInfo? = null
    )

    override fun <V> mine(
        database: List<V>,
        predicates: List<Predicate<V>>,
        targets: List<Predicate<V>>,
        predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
        params: Map<String, Any>
    ): List<List<Node<V>>> {
        try {
            val sourcesToTargets = (if (targets.isNotEmpty()) targets else predicates).map { target ->
                predicates.filter { it.name() != target.name() } to target
            }

            return mine(
                "All => All",
                database,
                sourcesToTargets,
                { },
                maxComplexity = params.getOrDefault("maxComplexity", 3) as Int,
                topPerComplexity = params.getOrDefault("topPerComplexity", TOP_PER_COMPLEXITY) as Int,
                function = params.getOrDefault("objectiveFunction", Rule<V>::conviction) as (Rule<V>) -> Double,
                or = false,
                negate = true,
                buildHeatmapAndUpset = false
            )
        } catch (t: Throwable) {
            t.printStackTrace()
            LOG.error(t.message)
            return emptyList()
        }
    }

    /**
     * Result of optimization is a graph, [Node] represents a single node of a graph.
     *
     * For each edge Parent -> Child, the following **invariant** is hold.
     * 1.   f(Child) >= f(Parent) + [functionDelta]
     * 2.   klDelta < 0 OR kullbackLeibler(dEmpirical, dChild) <= kullbackLeibler(dEmpirical, dParent) - [klDelta]
     *
     * How information is used?
     * 1.   Consider all the atomics in Parent and Child, [EmpiricalDistribution] dEmpirical
     *      is experimental joint distribution on all the atomics.
     * 2.   Starting with independent [Distribution] we can estimate how many information we get by rules:
     *      learn(dIndependent, Parent rule) = dParent
     *      learn(dIndependent, Child rule) = dChild
     *      kullbackLeibler(dEmpirical, dParent) - "distance" to empirical distribution
     *      kullbackLeibler(dEmpirical, dChild) - "distance" to empirical distribution
     *      If "distance" difference becomes too small, stop optimization.
     *
     * Important:
     *      kullbackLeibler(dEmpirical, dIndependent) - kullbackLeibler(dEmpirical) is a constant value,
     *      independent on adding extra atomics.
     *      This allows us to talk about learning "enough" information about the system.
     */
    fun <T> mine(
        title: String,
        database: List<T>,
        toMine: List<Pair<List<Predicate<T>>, Predicate<T>>>,
        logFunction: (List<Node<T>>) -> Unit,
        maxComplexity: Int,
        and: Boolean = true,
        or: Boolean = true,
        negate: Boolean = true,
        topPerComplexity: Int = TOP_PER_COMPLEXITY,
        function: (Rule<T>) -> Double = Rule<T>::conviction,
        functionDelta: Double = FUNCTION_DELTA,
        klDelta: Double = KL_DELTA,
        buildHeatmapAndUpset: Boolean = false
    ): List<List<Node<T>>> {
        LOG.info("Rules mining: $title")
        toMine.forEach { (conditions, target) ->
            // For each complexity level and for aux info computation
            MultitaskProgress.addTask(target.name(), min(conditions.size, maxComplexity) + 1L)
        }
        val mineResults = toMine.map { (conditions, target) ->
            // Cleanup predicates cache
            Predicate.dbCache = null
            val mineResult = mine(
                conditions, target, database,
                maxComplexity, and, or, negate,
                topPerComplexity, function,
                functionDelta, klDelta, buildHeatmapAndUpset
            )
            logFunction(mineResult)
            mineResult
        }
        LOG.info("DONE rules mining: $title")
        return mineResults
    }


    @VisibleForTesting
    internal fun <T> mine(
        predicates: List<Predicate<T>>,
        target: Predicate<T>,
        database: List<T>,
        maxComplexity: Int,
        and: Boolean = true,
        or: Boolean = true,
        negate: Boolean = true,
        topPerComplexity: Int = TOP_PER_COMPLEXITY,
        function: (Rule<T>) -> Double,
        functionDelta: Double = FUNCTION_DELTA,
        klDelta: Double = KL_DELTA,
        buildHeatmapAndUpset: Boolean = true
    ): List<Node<T>> {
        // Since we use FishBone visualization as an analysis method,
        // we want all the results available for each complexity level available for inspection
        val best = mineByComplexity(
            predicates, target, database,
            maxComplexity, and, or, negate,
            topPerComplexity, function,
            functionDelta, klDelta
        )

        // NOTE[shpynov] hacks adding target information to all the nodes
        val singleRules = best[1]

        // We don't need calculate additional statistics in case of sampling
        val targetAux = if (buildHeatmapAndUpset && singleRules.isNotEmpty()) {
            // Collect pairwise correlations and all the top level predicates combinations
            TargetVisualizeInfo(
                Miner.heatmap(database, target, singleRules),
                Miner.upset(database, target, singleRules)
            )
        } else null

        val result = best.flatMap { it }.sortedWith<Node<T>>(RulesBPQ.comparator(function))
        result.map {
            Callable {
                it.visualizeInfo = RuleVisualizeInfo(
                    rule =
                    Combinations.of(
                        database,
                        listOfNotNull(it.element, it.parent?.rule?.conditionPredicate, it.rule.targetPredicate)
                    )
                )
            }
        }.await(parallel = true)
        MultitaskProgress.finishTask(target.name())
        return result +
                // Technical rule TRUE => target
                listOf(Node(Rule(TruePredicate(), target, database), TruePredicate(), null, targetAux))
    }


    /**
     * Dynamic programming algorithm storing optimization results by complexity.
     */
    private fun <T> mineByComplexity(
        predicates: List<Predicate<T>>,
        target: Predicate<T>,
        database: List<T>,
        maxComplexity: Int,
        and: Boolean = true,
        or: Boolean = true,
        negate: Boolean = true,
        topPerComplexity: Int = TOP_PER_COMPLEXITY,
        function: (Rule<T>) -> Double,
        functionDelta: Double = FUNCTION_DELTA,
        klDelta: Double = KL_DELTA
    ): Array<RulesBPQ<T>> {
        if (klDelta <= 0) {
            LOG.debug("Information criterion check ignored")
        }
        check(klDelta <= 1) {
            "Expected klDelta <= 1 (100%), got: $klDelta"
        }
        // Invariant: best[k] - best optimization results with complexity = k
        val bestByComplexity = Array(maxComplexity + 1) {
            RulesBPQ(topPerComplexity, database, function, functionDelta, klDelta)
        }
        val executor = Executors.newWorkStealingPool(parallelismLevel())
        (1..min(maxComplexity, predicates.size)).forEach { k ->
            val queue = bestByComplexity[k]
            if (k == 1) {
                executor.awaitAll(predicates.flatMap { p ->
                    (if (p.canNegate() && negate) listOf(p, p.not()) else listOf(p))
                }.map { p ->
                    Callable {
                        queue.add(Node(Rule(p, target, database), p, null))
                    }
                })
            } else {
                executor.awaitAll(bestByComplexity[k - 1].flatMap { parent ->
                    val startAtomics = parent.rule.conditionPredicate.collectAtomics() + target
                    predicates.filter { it !in startAtomics }
                        .flatMap { p -> if (p.canNegate() && negate) listOf(p, p.not()) else listOf(p) }
                        .flatMap { p ->
                            PredicatesInjector.injectPredicate(parent.rule.conditionPredicate, p, and = and, or = or)
                                .filter(Predicate<T>::defined).map {
                                    Triple(parent, p, it)
                                }
                        }
                }.map { (parent, p, candidate) ->
                    Callable {
                        queue.add(Node(Rule(candidate, target, database), p, parent))
                    }
                })
            }
            MultitaskProgress.reportTask(target.name())
        }
        executor.shutdown()
        return bestByComplexity
    }

}