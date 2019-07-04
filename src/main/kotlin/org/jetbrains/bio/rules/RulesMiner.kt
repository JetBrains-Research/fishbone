package org.jetbrains.bio.rules

import com.google.common.annotations.VisibleForTesting
import org.apache.commons.csv.CSVFormat
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.RulesMiner.mine
import org.jetbrains.bio.util.MultitaskProgress
import org.jetbrains.bio.util.awaitAll
import org.jetbrains.bio.util.bufferedReader
import org.jetbrains.bio.util.parallelismLevel
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.min


object RulesMiner {

    const val TOP_PER_COMPLEXITY = 100
    const val TOP_LEVEL_PREDICATES_INFO = 10
    const val FUNCTION_DELTA = 1E-3
    const val KL_DELTA = 1E-3


    private val LOG = Logger.getLogger(RulesMiner::class.java)

    /**
     * Result of [mine] procedure.
     */
    data class Node<T>(val rule: Rule<T>, val element: Predicate<T>, val parent: Node<T>?, var aux: Aux? = null)

    /**
     * Auxiliary info for visualization purposes.
     *
     * @param rule represents joint distribution (condition, parent?, target)
     * @param target represents all the top level predicates pairwise joint distributions
     */
    data class Aux(val rule: DistributionPP, val target: List<DistributionPP>? = null)


    /**
     * Dynamic programming algorithm storing optimization results by complexity.
     */
    private fun <T> mineByComplexity(predicates: List<Predicate<T>>,
                                     target: Predicate<T>,
                                     database: List<T>,
                                     maxComplexity: Int,
                                     and: Boolean = true,
                                     or: Boolean = true,
                                     topPerComplexity: Int = TOP_PER_COMPLEXITY,
                                     topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
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
        val bestByComplexity = Array(maxComplexity + 1) { RulesBPQ(topPerComplexity, database, function, functionDelta, klDelta) }
        (1..min(maxComplexity, predicates.size)).forEach { k ->
            val queue = bestByComplexity[k]
            if (k == 1) {
                predicates.forEach { p ->
                    MultitaskProgress.reportTask(target.name())
                    (if (p.canNegate()) listOf(p, p.not()) else listOf(p))
                            .forEach { queue.add(Node(Rule(it, target, database), it, null)) }
                }
                // Collect all the top level predicates pairwise joint distributions
                val topLevelNodes = queue.sortedWith(RulesBPQ.comparator(function)).take(topLevelToPredicatesInfo)
                val topLevelPairwiseDistributions = arrayListOf<DistributionPP>()
                for (i in 0 until topLevelNodes.size) {
                    val n1 = topLevelNodes[i]
                    (i + 1 until topLevelNodes.size).forEach { j ->
                        val n2 = topLevelNodes[j]
                        topLevelPairwiseDistributions.add(EmpiricalDistribution(database, listOf(
                                n1.rule.conditionPredicate, n2.rule.conditionPredicate, target)).pp())
                    }
                }
                // NOTE[shpynov] hacks adding target information to all the nodes
                for (i in 0 until topLevelNodes.size) {
                    val nI = topLevelNodes[i]
                    nI.aux = Aux(
                            rule = EmpiricalDistribution(database, listOf(nI.rule.conditionPredicate, target)).pp(),
                            target = topLevelPairwiseDistributions)
                }
            } else {
                bestByComplexity[k - 1].flatMap { parent ->
                    val startAtomics = parent.rule.conditionPredicate.collectAtomics() + target
                    predicates.filter { MultitaskProgress.reportTask(target.name()); it !in startAtomics }
                            .flatMap { p -> if (p.canNegate()) listOf(p, p.not()) else listOf(p) }
                            .flatMap { p ->
                                PredicatesInjector.injectPredicate(parent.rule.conditionPredicate, p)
                                        .filter(Predicate<T>::defined)
                                        .map { Node(Rule(it, target, database), p, parent) }
                            }
                }.forEach { queue.add(it) }
            }
        }
        return bestByComplexity
    }


    @VisibleForTesting
    internal fun <T> mine(predicates: List<Predicate<T>>,
                          target: Predicate<T>,
                          database: List<T>,
                          maxComplexity: Int,
                          and: Boolean = true,
                          or: Boolean = true,
                          topPerComplexity: Int = TOP_PER_COMPLEXITY,
                          topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
                          function: (Rule<T>) -> Double,
                          functionDelta: Double = FUNCTION_DELTA,
                          klDelta: Double = KL_DELTA): List<Node<T>> {
        val best = mineByComplexity(predicates, target, database,
                maxComplexity, and, or,
                topPerComplexity, topLevelToPredicatesInfo,
                function, functionDelta, klDelta)
        // Since we use FishBone visualization as an analysis method,
        // we want all the results available for each complexity level available for inspection
        return best.flatMap { it }.sortedWith<Node<T>>(RulesBPQ.comparator(function))
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
                 topPerComplexity: Int = TOP_PER_COMPLEXITY,
                 topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
                 function: (Rule<T>) -> Double = Rule<T>::conviction,
                 functionDelta: Double = FUNCTION_DELTA,
                 klDelta: Double = KL_DELTA) {
        LOG.info("Rules mining: $title")
        // Mine each target separately
        val executor = Executors.newWorkStealingPool(parallelismLevel())
        executor.awaitAll(
                toMine.map { (conditions, target) ->
                    MultitaskProgress.addTask(target.name(),
                            conditions.size + conditions.size.toLong() * (maxComplexity - 1) * topPerComplexity)
                    Callable {
                        val mineResult = mine(conditions, target, database,
                                maxComplexity, and, or,
                                topPerComplexity, topLevelToPredicatesInfo,
                                function, functionDelta, klDelta)
                        logFunction(mineResult)
                        MultitaskProgress.finishTask(target.name())
                    }
                })
        check(executor.shutdownNow().isEmpty())
        LOG.info("DONE rules mining: $title")
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