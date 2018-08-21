package org.jetbrains.bio.rules

import com.google.common.collect.Maps
import com.google.common.primitives.Doubles
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

    /**
     * Bounded Priority Queue.
     * Stores top [limit] items, prioritized by [comparator]
     * @param limit Max queue size
     * @param comparator Comparator to maintain desired order
     */
    class BPQ<T>(private val limit: Int,
                 private val comparator: Comparator<in Node<T>>,
                 private val queue: Queue<Node<T>> = PriorityQueue(limit, comparator.reversed()))
        : Queue<Node<T>> by queue {

        override fun add(element: Node<T>): Boolean = offer(element)

        override fun offer(e: Node<T>): Boolean {
            val condition = e.rule.conditionPredicate
            val oldNode = queue.find { it.rule.conditionPredicate == condition }
            // Compare nodes with same condition, but different parents. Compare parents in this case
            if (oldNode != null) {
                if (oldNode.parent == null) {
                    return false
                }
                if (e.parent == null || comparator.compare(e.parent, oldNode.parent) <= 0) {
                    remove(oldNode)
                } else {
                    return false
                }
            }
            /**
             * Main difference with [PriorityQueue], huge optimization!
             */
            if (size >= limit) {
                val head = peek()
                // NOTE[shpynov] queue is built upon reversed comparator
                if (comparator.compare(e, head) > -1) {
                    return false
                }
                poll()
            }

            return queue.offer(e)
        }
    }

    private val LOG = Logger.getLogger(RM::class.java)

    /**
     * Result of [optimize] procedure.
     */
    data class Node<T>(val rule: Rule<T>, val element: Predicate<T>, val parent: Node<T>?) {
        companion object {
            fun <T> comparator() = Comparator<Node<T>> { (r1, _), (r2, _) ->
                -Doubles.compare(r1.conviction, r2.conviction)
            }
        }
    }


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
                              topResults: Int,
                              convictionDelta: Double,
                              klDelta: Double): List<Node<T>> {
        if (klDelta <= 0) {
            LOG.debug("Information criterion check ignored")
        }
        check(klDelta <= 1) {
            "Expected klDelta <= 1 (100%), got: $klDelta"
        }
        val comparator = Node.comparator<T>()
        // Invariant: best[k] - best predicates with complexity = k
        val best = Array(maxComplexity + 1) { BPQ(topResults, comparator) }
        (1..Math.min(maxComplexity, predicates.size)).forEach { k ->
            val queue = best[k]
            if (k == 1) {
                predicates.forEach { p ->
                    MultitaskProgress.reportTask(target.name())
                    (if (p.canNegate()) listOf(p, p.negate()) else listOf(p))
                            .filter { it.complexity() == k }
                            .forEach { queue.add(Node(Rule(it, target, database), it, null)) }
                }
            } else {
                best[k - 1].flatMap { parent ->
                    val startConviction = parent.rule.conviction
                    val startAtomics = parent.rule.conditionPredicate.collectAtomics() + target
                    predicates.filter { MultitaskProgress.reportTask(target.name()); it !in startAtomics }
                            .flatMap { p -> if (p.canNegate()) listOf(p, p.negate()) else listOf(p) }
                            .flatMap { p ->
                                PredicatesInjector.injectPredicate(parent.rule.conditionPredicate, p)
                                        .filter(Predicate<T>::defined)
                                        .map {
                                            Node(Rule(it, target, database), p, parent)
                                        }
                                        .filter {
                                            val newConviction = it.rule.conviction
                                            if (newConviction < startConviction + convictionDelta) {
                                                return@filter false
                                            }
                                            // If klDelta <= 0 ignore information check
                                            if (klDelta > 0) {
                                                val atomics = (startAtomics + p.collectAtomics()).distinct()
                                                val empirical = EmpiricalDistribution(database, atomics)
                                                val independent = Distribution(database, atomics)
                                                val kl = KL(empirical, independent)
                                                val klParent = KL(empirical, independent.learn(parent.rule))
                                                val klRule = KL(empirical, independent.learn(it.rule))
                                                check(klRule < kl) {
                                                    "KL after learning rule should be closer to empirical than independent"
                                                }
                                                // Check that we gained at least klDelta improvement
                                                if (klRule >= klParent - klDelta * kl) {
                                                    return@filter false
                                                }
                                                LOG.debug("C: $newConviction | $startConviction\t" +
                                                        "S: ${it.rule.conviction} | ${parent.rule.conviction}\t" +
                                                        "KL(empirical, rule): $klRule | $klParent\t" +
                                                        "${it.rule.conditionPredicate.name()} | ${parent.rule.name}")
                                            } else {
                                                LOG.debug("C: $newConviction | $startConviction\t" +
                                                        "S: ${it.rule.conviction} | ${parent.rule.conviction}\t" +
                                                        "${it.rule.conditionPredicate.name()} | ${parent.rule.name}")

                                            }
                                            return@filter true
                                        }
                            }
                }.forEach { queue.add(it) }
            }
        }
        val result = best.flatMap { it }.sortedWith(comparator).take(topResults)
        MultitaskProgress.finishTask(target.name())
        return result
    }


    fun <T> mine(title: String, database: List<T>,
                 toMine: List<Pair<List<Predicate<T>>, Predicate<T>>>,
                 logFunction: (List<Node<T>>) -> Unit,
                 maxComplexity: Int,
                 topResults: Int = 100,
                 convictionDelta: Double = 1E-2,
                 klDelta: Double = 1E-2) {
        LOG.info("RM processing: $title")
        // Mine each target separately
        val executor = Executors.newWorkStealingPool(parallelismLevel())
        executor.awaitAll(
                toMine.map { (conditions, target) ->
                    MultitaskProgress.addTask(target.name(),
                            conditions.size + conditions.size.toLong() * (maxComplexity - 1) * topResults)
                    Callable {
                        logFunction(optimize(conditions, target, database, maxComplexity, topResults, convictionDelta, klDelta))
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