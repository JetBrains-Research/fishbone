package org.jetbrains.bio.rules.ripper

import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.AndPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.Rule
import org.jetbrains.bio.rules.RulesMiner
import weka.classifiers.rules.JRip
import weka.classifiers.rules.RuleStats
import weka.core.Attribute
import weka.core.Instances

/**
 * This miner rus Ripper algorithm (see: https://www.sciencedirect.com/science/article/pii/B9781558603776500232)
 * on specified data.
 * This class uses JRipper class from Weka library (see: http://weka.sourceforge.net/doc.dev/weka/classifiers/rules/JRip.html)
 */
class RipperMiner {
    companion object {
        private val LOG = Logger.getLogger(RipperMiner::class.java)

        fun <V> mine(
                instancesByTarget: Map<Predicate<V>, Instances>,
                predicates: Map<String, Predicate<V>>,
                database: List<V>
        ): List<List<RulesMiner.Node<V>>> {
            return instancesByTarget.map { (target, instances) ->
                val jRip = JRip()
                jRip.buildClassifier(instances)
                val classAttribute = instances.attribute(instances.numAttributes() - 1)

                LOG.info(rulesetString(jRip, classAttribute, instances))

                buildRuleNodes(jRip, predicates, target, database)
            }
        }

        private fun <V> buildRuleNodes(
                jRip: JRip, predicates: Map<String, Predicate<V>>, target: Predicate<V>, database: List<V>
        ): List<RulesMiner.Node<V>> {
            return (0 until 2).map { classIndex ->
                val ruleStats = jRip.getRuleStats(classIndex)
                val rules = ruleStats.ruleset
                rules
                        .filter { it.hasAntds() } //TODO: what to do with default rule?
                        .map {
                            val rule = it as JRip.RipperRule
                            val antdsPredicates = rule.antds
                                    .filter { predicates.containsKey(it.attr.name()) }
                                    .map {
                                        val predicate = predicates.getValue(it.attr.name())
                                        if (it.attrValue.toInt() == 0) predicate.not() else predicate
                                    }
                            if (antdsPredicates.isNotEmpty()) {
                                val first = antdsPredicates.first()
                                antdsPredicates.drop(1).fold(
                                        listOf(RulesMiner.Node(Rule(first, target, database), first, null)),
                                        { nodes, p ->
                                            val parent = nodes.last()
                                            val newPredicate = AndPredicate(listOf(parent.rule.conditionPredicate, p))
                                            nodes + RulesMiner.Node(Rule(newPredicate, target, database), p, parent)
                                        }
                                )
                            } else {
                                emptyList()
                            }
                        }.flatten()
            }.flatten()
        }

        private fun rulesetString(jRip: JRip, classAttribute: Attribute?, instances: Instances): String {
            return (0 until 2).joinToString(separator = ",\n") { classIndex ->
                val ruleStats = jRip.getRuleStats(classIndex)
                val rules = ruleStats.ruleset
                (0 until rules.size).joinToString(separator = ",\n") { ruleIndex ->
                    val rule = rules[ruleIndex]

                    val prefix = if (!rule.hasAntds()) "() " else ""
                    val ruleString = prefix + (rule as JRip.RipperRule).toString(classAttribute)

                    ruleString + "\n" + ruleStatString(ruleStats, ruleIndex, instances)
                }
            }
        }

        private fun ruleStatString(ruleStats: RuleStats, ruleIndex: Int, instances: Instances): String {
            val simStats = ruleStats.getSimpleStats(ruleIndex)
            val len = instances.size

            val support = simStats[0] / len
            val accuracy = (simStats[2] + simStats[3]) / len
            val precision = simStats[2] / (simStats[2] + simStats[4])
            val recall = simStats[2] / (simStats[2] + simStats[5])

            return "support: ${round(support)}, " +
                    "accuracy: ${round(accuracy)}, " +
                    "precision: ${round(precision)}, " +
                    "recall: ${round(recall)}"
        }

        private fun round(value: Double): String {
            return "%.4f".format(value)
        }
    }
}