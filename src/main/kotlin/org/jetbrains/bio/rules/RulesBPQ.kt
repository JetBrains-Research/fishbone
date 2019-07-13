package org.jetbrains.bio.rules

import com.google.common.collect.ComparisonChain
import org.apache.log4j.Logger
import org.jetbrains.bio.rules.RulesMiner.mine
import java.util.*

/**
 * Thread safe Bounded Priority Queue.
 * Stores top [limit] items, prioritized by [comparator]
 */
class RulesBPQ<T>(private val limit: Int,
                  private val database: List<T>,
                  private val function: (Rule<T>) -> Double = Rule<T>::conviction,
                  private val functionDelta: Double,
                  private val klDelta: Double,
                  private val comparator: Comparator<RulesMiner.Node<T>> = comparator(function),
                  private val queue: Queue<RulesMiner.Node<T>> = PriorityQueue(limit, comparator.reversed()))
    : Queue<RulesMiner.Node<T>> by queue {

    override fun add(element: RulesMiner.Node<T>): Boolean = offer(element)

    override fun offer(node: RulesMiner.Node<T>): Boolean {
        val rule = node.rule
        val parent = node.parent
        val condition = rule.conditionPredicate

        // Check that construction vs parent is correct
        if (parent != null && !checkHierarchy(node, parent, function)) {
            return false
        }
        synchronized(this) {
            // NOTE[shpynov] main difference with [PriorityQueue]!!!
            if (size >= limit) {
                val head = peek()
                // NOTE[shpynov] queue is built upon reversed comparator
                if (comparator.compare(node, head) > -1) {
                    LOG.debug("FAILED function check against smallest in queue  ${function(rule)} < ${function(head.rule)}\n" +
                            "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent?.rule?.name}")
                    return false
                }
            }
            // Compare nodes with same condition, but different parents, compare parents in this case
            if (condition.complexity() > 1) {
                val sameConditionNode = queue.firstOrNull { it.rule.conditionPredicate == condition }
                if (sameConditionNode != null) {
                    return if (comparator.compare(parent, sameConditionNode.parent) < 0) {
                        LOG.debug("REMOVING from queue same condition\n" +
                                "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent!!.rule.name}")
                        remove(sameConditionNode)
                        queue.offer(node)
                    } else {
                        LOG.debug("FAILED other parent check for same condition " +
                                "${sameConditionNode.parent!!.rule.conditionPredicate.name()} > ${parent!!.rule.conditionPredicate.name()}\n" +
                                "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                        false
                    }
                }
            }
            // NOTE[shpynov] main difference with [PriorityQueue]!!!
            if (size >= limit) {
                LOG.debug("REDUCING queue\n" +
                        "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent?.rule?.name}")
                poll()
            }
            return queue.offer(node)
        }
    }


    /**
     * Check necessary function and information gain vs parent node
     * See [mine] for details on thresholds
     */
    private fun checkHierarchy(
            node: RulesMiner.Node<T>,
            parent: RulesMiner.Node<T>,
            function: (Rule<T>) -> Double): Boolean {
        // Check order
        val elementF = function(Rule(node.element, node.rule.targetPredicate, database))
        val parentF = function(parent.rule)
        if (elementF > parentF) {
            LOG.debug("FAILED Function Order element vs parent delta check $elementF > $parentF\n" +
                    "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
            return false
        }
        // Check function delta
        val nodeF = function(node.rule)
        if (nodeF < parentF + functionDelta) {
            LOG.debug("FAILED Function node vs parent delta check " +
                    "$nodeF < $parentF + $functionDelta\n" +
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
            check(klRule < klIndependent + 1e-10) {
                "kullbackLeibler after learning rule should be closer to empirical than independent"
            }
            // Check that we gained at least klDelta improvement
            if (klRule >= klParent - klDelta * klIndependent) {
                LOG.debug("FAILED Information delta node vs parent on full check " +
                        "$klRule >= $klParent - $klDelta * $klIndependent\n" +
                        "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
                return false
            }
        }
        LOG.debug("PASS rule\n" +
                "+ ${node.element.name()}, ${node.rule.conditionPredicate.name()} | ${parent.rule.name}")
        return true
    }

    companion object {
        private val LOG = Logger.getLogger(RulesBPQ::class.java)

        /**
         * @return Comparator by max function and min complexity
         */
        fun <T> comparator(function: (Rule<T>) -> Double) =
                Comparator<RulesMiner.Node<T>> { (r1, _), (r2, _) ->
                    ComparisonChain.start()
                            .compare(function(r2), function(r1))
                            .compare(r1.conditionPredicate.complexity(), r2.conditionPredicate.complexity())
                            .result()
                }
    }
}
