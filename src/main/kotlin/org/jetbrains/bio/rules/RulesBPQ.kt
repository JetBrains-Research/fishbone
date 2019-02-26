package org.jetbrains.bio.rules

import com.google.common.primitives.Doubles
import com.google.common.primitives.Ints
import org.apache.log4j.Logger
import org.jetbrains.bio.rules.RulesMiner.mine
import java.util.*

/**
 * Bounded Priority Queue.
 * Stores top [limit] items, prioritized by [comparator]
 */
class RulesBPQ<T>(private val limit: Int,
                  private val database: List<T>,
                  private val convictionDelta: Double,
                  private val klDelta: Double,
                  private val comparator: Comparator<RulesMiner.Node<T>> = comparator(),
                  private val queue: Queue<RulesMiner.Node<T>> = PriorityQueue(limit, comparator.reversed()))
    : Queue<RulesMiner.Node<T>> by queue {

    override fun add(element: RulesMiner.Node<T>): Boolean = offer(element)

    override fun offer(node: RulesMiner.Node<T>): Boolean {
        val rule = node.rule
        val parent = node.parent
        val condition = rule.conditionPredicate
        var convictionAndKLChecked: Boolean? = null
        if (condition.complexity() > 1) {
            val oldNode = queue.singleOrNull { it.rule.conditionPredicate == condition }
            // Compare nodes with same condition, but different parents, compare parents in this case
            if (oldNode != null) {
                if (comparator.compare(parent, oldNode.parent) <= 0) {
                    convictionAndKLChecked = checkConvictionAndKLDeltas(node, parent)
                    if (convictionAndKLChecked == true) {
                        LOG.debug("REMOVING from queue same condition\n" +
                                "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent?.rule?.name}")
                        remove(oldNode)
                    } else {
                        return false
                    }
                } else {
                    LOG.debug("FAILED parent check for same condition " +
                            "${oldNode.parent!!.rule.conditionPredicate.name()} > ${parent!!.rule.conditionPredicate.name()}\n" +
                            "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                    return false
                }
            }
        }
        /**
         * Note(Shpynov) main difference with [PriorityQueue]!!!
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
                    (convictionAndKLChecked == false || !checkConvictionAndKLDeltas(node, parent))) {
                return false
            }
            LOG.debug("REDUCING queue\n" +
                    "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent?.rule?.name}")
            poll()
            return queue.offer(node)
        } else {
            if (convictionAndKLChecked != true &&
                    (convictionAndKLChecked == false || !checkConvictionAndKLDeltas(node, parent))) {
                return false
            }
            return queue.offer(node)
        }
    }


    /**
     * See [mine] for details on thresholds
     */
    private fun checkConvictionAndKLDeltas(node: RulesMiner.Node<T>, parent: RulesMiner.Node<T>?): Boolean {
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
                LOG.debug("FAILED Conviction rule vs parent delta check " +
                        "$convictionRule < ${parent.rule.conviction} + $convictionDelta\n" +
                        "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                return false
            }
            // If klDelta <= 0 ignore information check
            if (klDelta > 0) {
                val atomics = (parent.rule.conditionPredicate.collectAtomics() +
                        node.element.collectAtomics() + listOf(node.rule.targetPredicate)).distinct()
                val empirical = EmpiricalDistribution(database, atomics)
                val independent = Distribution(database, atomics)
                val klParent = Distribution.kullbackLeibler(empirical, independent.learn(parent.rule))
                val klIndependent = Distribution.kullbackLeibler(empirical, independent)
                val klRule = Distribution.kullbackLeibler(empirical, independent.learn(node.rule))
                check(klRule < klIndependent) {
                    "kullbackLeibler after learning rule should be closer to empirical than independent"
                }
                // Check that we gained at least klDelta improvement
                if (klRule >= klParent - klDelta * klIndependent) {
                    LOG.debug("FAILED Information delta rule vs parent on full check " +
                            "$klRule >= $klParent - $klDelta * $klIndependent\n" +
                            "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                    return false
                }
                node.aux = RulesMiner.Aux(rule = EmpiricalDistribution(database,
                        listOf(node.element, node.parent!!.rule.conditionPredicate, node.rule.targetPredicate))
                        .pp())
            }
            LOG.debug("PASS rule\n" +
                    "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
        }
        return true
    }

    companion object {
        private val LOG = Logger.getLogger(RulesBPQ::class.java)

        /**
         * @return Comparator by max conviction and min complexity
         */
        fun <T> comparator() = Comparator<RulesMiner.Node<T>> { (r1, _), (r2, _) ->
            val convictionCompare = -Doubles.compare(r1.conviction, r2.conviction)
            if (convictionCompare != 0) {
                return@Comparator convictionCompare
            }
            return@Comparator Ints.compare(r1.conditionPredicate.complexity(), r2.conditionPredicate.complexity())
        }
    }
}
