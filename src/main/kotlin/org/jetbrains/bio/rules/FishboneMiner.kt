package org.jetbrains.bio.rules

import com.google.common.annotations.VisibleForTesting
import org.apache.commons.csv.CSVFormat
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.FishboneMiner.mine
import org.jetbrains.bio.util.*
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.min


object FishboneMiner: Miner {
    const val TOP_PER_COMPLEXITY = 100
    const val TOP_LEVEL_PREDICATES_INFO = 10
    const val FUNCTION_DELTA = 1E-3
    const val KL_DELTA = 1E-3


    private val LOG = Logger.getLogger(FishboneMiner::class.java)

    /**
     * Result of [mine] procedure.
     */
    data class Node<T>(val rule: Rule<T>, val element: Predicate<T>, val parent: Node<T>?, var aux: Aux? = null)

    /**
     * Auxiliary info for visualization purposes.
     *
     * @param rule represents joint distribution (condition, parent?, target)
     * @param target represents all the top level predicates pairwise combinations
     */
    data class Aux(val rule: Upset, val target: List<Upset>? = null)

    override fun <V> mine(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
            params: Map<String, Any>
    ): List<List<Node<V>>> {
        try {
            LOG.info("Processing fishbone")

            val sourcesToTargets = if (targets.isNotEmpty()) {
                targets.map { target -> predicates to target }
            } else {
                mapAllPredicatesToAll(predicates, predicates.withIndex())
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

    private fun <V> mapAllPredicatesToAll(
            predicates: List<Predicate<V>>, indexedPredicates: Iterable<IndexedValue<Predicate<V>>>
    ): List<Pair<List<Predicate<V>>, Predicate<V>>> {
        return (0 until predicates.size).map { i ->
            val target = predicates[i]
            val sources = indexedPredicates.filter { (j, _) -> j != i }.map { (_, value) -> value }
            sources to target
        }.toList()
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
                          topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
                          function: (Rule<T>) -> Double,
                          functionDelta: Double = FUNCTION_DELTA,
                          klDelta: Double = KL_DELTA): List<Node<T>> {
        // Since we use FishBone visualization as an analysis method,
        // we want all the results available for each complexity level available for inspection
        val best = mineByComplexity(predicates, target, database,
                maxComplexity, and, or, negate,
                topPerComplexity, function,
                functionDelta, klDelta)
        // Fill aux information
        val topLevelNodes = best[1].sortedWith(RulesBPQ.comparator(function)).take(topLevelToPredicatesInfo)
        // Collect all the top level predicates pairwise joint distributions
        val topLevelPairwiseCombinations = arrayListOf<Upset>()
        for (i in 0 until topLevelNodes.size) {
            val n1 = topLevelNodes[i]
            (i + 1 until topLevelNodes.size).forEach { j ->
                val n2 = topLevelNodes[j]
                topLevelPairwiseCombinations.add(Upset.of(database,
                        listOf(n1.rule.conditionPredicate, n2.rule.conditionPredicate, target)))
            }
        }
        // NOTE[shpynov] hacks adding target information to all the nodes
        for (i in 0 until topLevelNodes.size) {
            val nI = topLevelNodes[i]
            nI.aux = Aux(
                    rule = Upset.of(database, listOf(nI.rule.conditionPredicate, target)),
                    target = topLevelPairwiseCombinations)
        }

        val result = best.flatMap { it }.sortedWith<Node<T>>(RulesBPQ.comparator(function))
        result.map {
            Callable {
                if (it.parent != null) {
                    it.aux = Aux(rule =
                    Upset.of(database, listOf(it.element, it.parent.rule.conditionPredicate, it.rule.targetPredicate)))
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
                 topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
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
                    topPerComplexity, topLevelToPredicatesInfo,
                    function, functionDelta, klDelta)
            logFunction(mineResult)
            mineResult
        }
        LOG.info("DONE rules mining: $title")
        return mineResults
    }

    fun loadRules(path: Path): List<RuleRecord<Any>> {
        val predicatesMap = hashMapOf<String, Predicate<Any>>()
        return CSVFormat.DEFAULT.withCommentMarker('#').withHeader().parse(path.bufferedReader()).use { parser ->
            parser.records.map {
                RuleRecord.fromCSV(it) { name ->
                    if (name in predicatesMap) {
                        return@fromCSV predicatesMap[name]!!
                    }
                    val p = object : Predicate<Any>() {
                        override fun test(item: Any) = false
                        override fun name(): String = name
                    }
                    predicatesMap[name] = p
                    return@fromCSV p
                }
            }
        }
    }
}