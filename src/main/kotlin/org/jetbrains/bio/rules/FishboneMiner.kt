package org.jetbrains.bio.rules

import com.google.common.annotations.VisibleForTesting
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.NotPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.FishboneMiner.mine
import org.jetbrains.bio.util.MultitaskProgress
import org.jetbrains.bio.util.await
import org.jetbrains.bio.util.awaitAll
import org.jetbrains.bio.util.parallelismLevel
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.min


object FishboneMiner: Miner {
    const val TOP_PER_COMPLEXITY = 100
    const val FUNCTION_DELTA = 1E-3
    const val KL_DELTA = 1E-3


    private val LOG = Logger.getLogger(FishboneMiner::class.java)

    /**
     * Result of [mine] procedure.
     */
    data class Node<T>(val rule: Rule<T>, val element: Predicate<T>, val parent: Node<T>?, var aux: Aux? = null)

    override fun <V> mine(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
            params: Map<String, Any>
    ): List<List<Node<V>>> {
        try {
            LOG.info("Processing fishbone")

            val sourcesToTargets = (if (targets.isNotEmpty()) targets else predicates).map { target ->
                predicates.filter { it.name() != target.name() } to target
            }

            return mine(
                    "All => All",
                    database,
                    sourcesToTargets,
                    { },
                    maxComplexity = params.getOrDefault("maxComplexity", 6) as Int,
                    function = params.getOrDefault("objectiveFunction", Rule<V>::conviction) as (Rule<V>) -> Double,
                    or = true,
                    negate = true
            )
        } catch (t: Throwable) {
            t.printStackTrace()
            LOG.error(t.message)
            return emptyList()
        }
    }

    /**
     * Dynamic programming algorithm storing optimization results by complexity.
     */
    private fun <T> mineByComplexity(predicates: List<Predicate<T>>,
                                     target: Predicate<T>,
                                     database: List<T>,
                                     maxComplexity: Int,
                                     and: Boolean = true,
                                     or: Boolean = true,
                                     negate: Boolean = true,
                                     topPerComplexity: Int = TOP_PER_COMPLEXITY,
                                     function: (Rule<T>) -> Double,
                                     functionDelta: Double = FUNCTION_DELTA,
                                     klDelta: Double = KL_DELTA): Array<RulesBPQ<T>> {
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


    @VisibleForTesting
    internal fun <T> mine(predicates: List<Predicate<T>>,
                          target: Predicate<T>,
                          database: List<T>,
                          maxComplexity: Int,
                          and: Boolean = true,
                          or: Boolean = true,
                          negate: Boolean = true,
                          topPerComplexity: Int = TOP_PER_COMPLEXITY,
                          function: (Rule<T>) -> Double,
                          functionDelta: Double = FUNCTION_DELTA,
                          klDelta: Double = KL_DELTA): List<Node<T>> {
        // Since we use FishBone visualization as an analysis method,
        // we want all the results available for each complexity level available for inspection
        val best = mineByComplexity(predicates, target, database,
                maxComplexity, and, or, negate,
                topPerComplexity, function,
                functionDelta, klDelta)

        // NOTE[shpynov] hacks adding target information to all the nodes
        val singleRules = best[1]

        // Pairwise correlations
        val heatmap = HeatMap.of(database,
                listOf(target) + singleRules.map { it.element }.filterNot { it is NotPredicate })

        // Collect all the top level predicates combinations
        val upset = Upset.of(database,
                singleRules.map { it.element }.filterNot { it is NotPredicate },
                target,
                listOf(3, maxComplexity, predicates.size + 1).min()!!)

        singleRules.map { sr ->
            Callable {
                sr.aux = Aux(
                        rule = Combinations.of(database, listOf(sr.rule.conditionPredicate, target)),
                        heatmap = heatmap,
                        upset = upset)
            }
        }.await(parallel = true)
        val result = best.flatMap { it }.sortedWith<Node<T>>(RulesBPQ.comparator(function))
        result.map {
            Callable {
                if (it.parent != null) {
                    it.aux = Aux(rule =
                    Combinations.of(database,
                            listOf(it.element, it.parent.rule.conditionPredicate, it.rule.targetPredicate)))
                }
            }
        }.await(parallel = true)
        MultitaskProgress.finishTask(target.name())
        return result
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
    fun <T> mine(title: String,
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
                 klDelta: Double = KL_DELTA): List<List<Node<T>>> {
        LOG.info("Rules mining: $title")
        toMine.forEach { (conditions, target) ->
            // For each complexity level and for aux info computation
            MultitaskProgress.addTask(target.name(), min(conditions.size, maxComplexity) + 1L)
        }
        val mineResults = toMine.map { (conditions, target) ->
            // Cleanup predicates cache
            Predicate.dbCache = null
            val mineResult = mine(conditions, target, database,
                    maxComplexity, and, or, negate,
                    topPerComplexity, function,
                    functionDelta, klDelta)
            logFunction(mineResult)
            mineResult
        }
        LOG.info("DONE rules mining: $title")
        return mineResults
    }

}