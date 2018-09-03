package org.jetbrains.bio.rules

import com.google.common.collect.Maps
import com.google.common.primitives.Doubles
import com.google.common.primitives.Ints
import org.apache.commons.csv.CSVFormat
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.RM.optimize
import org.jetbrains.bio.util.MultitaskProgress
import org.jetbrains.bio.util.awaitAll
import org.jetbrains.bio.util.bufferedReader
import org.jetbrains.bio.util.parallelismLevel
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors


object RM {

    const val TOP_PER_COMPLEXITY = 100
    const val TOP_LEVEL_PREDICATES_INFO = 10
    const val CONVICTION_DELTA = 1E-3
    const val KL_DELTA = 1E-3

    /**
     * Bounded Priority Queue.
     * Stores top [limit] items, prioritized by [comparator]
     * @param limit Max queue size
     */
    class BPQ<T>(private val limit: Int,
                 private val database: List<T>,
                 private val convictionDelta: Double,
                 private val klDelta: Double,
                 private val comparator: Comparator<Node<T>> = comparator(),
                 private val queue: Queue<Node<T>> = PriorityQueue(limit, comparator.reversed()))
        : Queue<Node<T>> by queue {

        override fun add(element: Node<T>): Boolean = offer(element)

        override fun offer(node: Node<T>): Boolean {
            val rule = node.rule
            val parent = node.parent
            val condition = rule.conditionPredicate
            var convictionAndKLChecked: Boolean? = null
            if (condition.complexity() > 1) {
                val oldNode = queue.singleOrNull { it.rule.conditionPredicate == condition }
                // Compare nodes with same condition, but different parents, compare parents in this case
                if (oldNode != null) {
                    if (comparator.compare(parent, oldNode.parent) <= 0) {
                        convictionAndKLChecked = checkConvictionAndKLThresholds(node, parent)
                        if (convictionAndKLChecked == true) {
                            LOG.debug("REMOVING from queue same condition\n" +
                                    "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent?.rule?.name}")
                            remove(oldNode)
                        } else {
                            return false
                        }
                    } else {
                        LOG.debug("FAILED parent check for same condition ${oldNode.parent!!.rule.conditionPredicate.name()} > ${parent!!.rule.conditionPredicate.name()}\n" +
                                "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                        return false
                    }
                }
            }
            /**
             * Note(Shpynov) main difference with [PriorityQueue] - huge optimization!
             */
            if (size >= limit) {
                val head = peek()
                // NOTE[shpynov] queue is built upon reversed comparator
                if (comparator.compare(node, head) > -1) {
                    LOG.debug("FAILED conviction check against smallest in queue  ${rule.conviction} < ${head.rule.conviction}\n" +
                            "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent?.rule?.name}")
                    return false
                }
                if (convictionAndKLChecked != true &&
                        (convictionAndKLChecked == false || !checkConvictionAndKLThresholds(node, parent))) {
                    return false
                }
                LOG.debug("REDUCING queue\n" +
                        "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent?.rule?.name}")
                poll()
                return queue.offer(node)
            } else {
                if (convictionAndKLChecked != true &&
                        (convictionAndKLChecked == false || !checkConvictionAndKLThresholds(node, parent))) {
                    return false
                }
                return queue.offer(node)
            }
        }


        /**
         * See [optimize] for details on thresholds
         */
        private fun checkConvictionAndKLThresholds(node: Node<T>, parent: Node<T>?): Boolean {
            // Check necessary conviction and information gain
            if (parent != null) {
                val convictionElement = Rule(node.element, node.rule.targetPredicate, database).conviction
                if (convictionElement > parent.rule.conviction) {
                    LOG.debug("FAILED Conviction element vs parent delta check $convictionElement > ${parent.rule.conviction}\n" +
                            "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                    return false
                }
                val convictionRule = node.rule.conviction
                if (convictionRule < parent.rule.conviction + convictionDelta) {
                    LOG.debug("FAILED Conviction rule vs parent delta check $convictionRule < ${parent.rule.conviction} + $convictionDelta\n" +
                            "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                    return false
                }
                // If klDelta <= 0 ignore information check
                if (klDelta > 0) {
                    val atomics = (parent.rule.conditionPredicate.collectAtomics() +
                            node.element.collectAtomics() + listOf(node.rule.targetPredicate)).distinct()
                    val empirical = EmpiricalDistribution(database, atomics)
                    val independent = Distribution(database, atomics)
                    val klParent = KL(empirical, independent.learn(parent.rule))
                    val klIndependent = KL(empirical, independent)
                    val klRule = KL(empirical, independent.learn(node.rule))
                    check(klRule < klIndependent) {
                        "KL after learning rule should be closer to empirical than independent"
                    }
                    // Check that we gained at least klDelta improvement
                    if (klRule >= klParent - klDelta * klIndependent) {
                        LOG.debug("FAILED Information delta rule vs parent on full check " +
                                "$klRule >= $klParent - $klDelta * $klIndependent\n" +
                                "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                        return false
                    }
                    node.aux = empirical.toJson()
                }
                LOG.debug("PASS rule\n" +
                        "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
            }
            return true
        }

        companion object {
            fun <T> comparator() = Comparator<Node<T>> { (r1, _), (r2, _) ->
                val convictionCompare = -Doubles.compare(r1.conviction, r2.conviction)
                if (convictionCompare != 0) {
                    return@Comparator convictionCompare
                }
                return@Comparator Ints.compare(r1.conditionPredicate.complexity(), r2.conditionPredicate.complexity())
            }
        }
    }

    private val LOG = Logger.getLogger(RM::class.java)

    /**
     * Result of [optimize] procedure.
     */
    data class Node<T>(val rule: Rule<T>, val element: Predicate<T>, val parent: Node<T>?, var aux: Any? = null)


    /**
     * Result of optimization is a graph, [Node] represents a single node of a graph.
     * For each edge Parent -> Child, the following invariant is hold:
     * 1. conviction(Child) >= conviction(Parent) + [convictionDelta]
     * 2. klDelta < 0 OR KL(dEmpirical, dChild) <= KL(dEmpirical, dParent) - [klDelta]
     * How information is used?
     * Consider all the atomics in Parent and Child, [EmpiricalDistribution] dEmpirical
     * is experimental joint distribution on all the atomics.
     * Starting with independent [Distribution] we can estimate how many information we get by rules:
     *  learn(dIndependent, Parent rule) = dParent
     *  learn(dIndependent, Child rule) = dChild
     *  KL(dEmpirical, dParent) - "distance left" to empirical distribution
     *  KL(dEmpirical, dChild) - "distance left" to empirical distribution
     * If "distance step" becomes too small, stop optimization.
     *
     * Important: KL(dEmpirical, dIndependent) - KL(dEmpirical) is a constant value, independent on adding extra atomics.
     * This allows us to talk about learning "enough" information about the system.
     */
    internal fun <T> optimize(predicates: List<Predicate<T>>,
                              target: Predicate<T>,
                              database: List<T>,
                              maxComplexity: Int,
                              topPerComplexity: Int = TOP_PER_COMPLEXITY,
                              topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
                              convictionDelta: Double = CONVICTION_DELTA,
                              klDelta: Double = KL_DELTA): List<Node<T>> {
        val best = optimizeByComplexity(predicates, target, database,
                maxComplexity, topPerComplexity, topLevelToPredicatesInfo, convictionDelta, klDelta)
        // Since we use FishBone visualization as an analysis method,
        // we want all the results available for each complexity level available for inspection
        val result = best.flatMap { it }.sortedWith(BPQ.comparator())
        MultitaskProgress.finishTask(target.name())
        return result
    }

    internal fun <T> optimizeByComplexity(predicates: List<Predicate<T>>,
                                          target: Predicate<T>,
                                          database: List<T>,
                                          maxComplexity: Int,
                                          topPerComplexity: Int = TOP_PER_COMPLEXITY,
                                          topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
                                          convictionDelta: Double = CONVICTION_DELTA,
                                          klDelta: Double = KL_DELTA): Array<BPQ<T>> {
        if (klDelta <= 0) {
            LOG.debug("Information criterion check ignored")
        }
        check(klDelta <= 1) {
            "Expected klDelta <= 1 (100%), got: $klDelta"
        }
        // Invariant: best[k] - best predicates with complexity = k
        val best = Array(maxComplexity + 1) { BPQ(topPerComplexity, database, convictionDelta, klDelta) }
        (1..Math.min(maxComplexity, predicates.size)).forEach { k ->
            val queue = best[k]
            if (k == 1) {
                predicates.forEach { p ->
                    MultitaskProgress.reportTask(target.name())
                    (if (p.canNegate()) listOf(p, p.not()) else listOf(p))
                            .forEach { queue.add(Node(Rule(it, target, database), it, null)) }
                }
                // Collect all the top level mutual aux information
                val topLevelPredicates = queue.sortedWith(BPQ.comparator()).take(topLevelToPredicatesInfo)

                for (i in 0 until topLevelPredicates.size) {
                    val n1 = topLevelPredicates[i]
                    val aux = (i + 1 until topLevelPredicates.size).associate { j ->
                        val n2 = topLevelPredicates[j]
                        n2.rule.conditionPredicate.name() to EmpiricalDistribution(database, listOf(
                                n1.rule.conditionPredicate, n2.rule.conditionPredicate, target)).toJson()
                    }
                    // Update aux
                    n1.aux = aux

                }
            } else {
                best[k - 1].flatMap { parent ->
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
        return best
    }


    fun <T> mine(title: String, database: List<T>,
                 toMine: List<Pair<List<Predicate<T>>, Predicate<T>>>,
                 logFunction: (List<Node<T>>) -> Unit,
                 maxComplexity: Int,
                 topPerComplexity: Int = TOP_PER_COMPLEXITY,
                 topLevelToPredicatesInfo: Int = TOP_LEVEL_PREDICATES_INFO,
                 convictionDelta: Double = CONVICTION_DELTA,
                 klDelta: Double = KL_DELTA) {
        LOG.info("RM processing: $title")
        // Mine each target separately
        val executor = Executors.newWorkStealingPool(parallelismLevel())
        executor.awaitAll(
                toMine.map { (conditions, target) ->
                    MultitaskProgress.addTask(target.name(),
                            conditions.size + conditions.size.toLong() * (maxComplexity - 1) * topPerComplexity)
                    Callable {
                        logFunction(optimize(conditions, target, database,
                                maxComplexity, topPerComplexity, topLevelToPredicatesInfo, convictionDelta, klDelta))
                    }
                })
        check(executor.shutdownNow().isEmpty())
        LOG.info("DONE RM processing: $title")
    }

    fun loadRules(path: Path): List<RuleRecord<Any>> {
        @Synchronized
        fun MutableMap<String, Predicate<Any>>.parse(name: String): Predicate<Any> {
            if (name in this) {
                return this[name]!!
            }
            val p = object : Predicate<Any>() {
                override fun test(item: Any) = false
                override fun name(): String = name
            }
            put(name, p)
            return p
        }

        val predicates = Maps.newConcurrentMap<String, Predicate<Any>>()

        return CSVFormat.DEFAULT.withCommentMarker('#').withHeader().parse(path.bufferedReader()).use { parser ->
            parser.records.map {
                RuleRecord.fromCSV(it, predicates::parse)
            }
        }
    }
}