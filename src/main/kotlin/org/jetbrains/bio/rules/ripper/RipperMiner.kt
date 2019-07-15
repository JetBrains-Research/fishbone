package org.jetbrains.bio.rules.ripper

import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.FishboneMiner
import org.jetbrains.bio.rules.WekaMiner
import weka.classifiers.rules.JRip
import weka.classifiers.rules.RuleStats
import weka.core.Instances

/**
 * This miner rus Ripper algorithm (see: https://www.sciencedirect.com/science/article/pii/B9781558603776500232)
 * on specified data.
 * This class uses JRipper class from Weka library (see: http://weka.sourceforge.net/doc.dev/weka/classifiers/rules/JRip.html)
 */
class RipperMiner : WekaMiner() {
    private val logger = Logger.getLogger(RipperMiner::class.java)

    override fun <V> mine(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
            params: Map<String, Any>
    ): List<List<FishboneMiner.Node<V>>> {
        val instancesByTarget = createInstances(database, predicates, targets, predicateCheck)
        val predicatesByName = (predicates + targets).associateBy { it.name() }

        return instancesByTarget.map { (target, instances) ->
            val jRip = JRip()
            jRip.buildClassifier(instances)

            logger.info(rulesetString(jRip, instances))

            buildRuleNodes(jRip, predicatesByName, target, database)
        }
    }

    private fun <V> buildRuleNodes(
            jRip: JRip, predicatesByName: Map<String, Predicate<V>>, target: Predicate<V>, database: List<V>
    ): List<FishboneMiner.Node<V>> {
        val classesRange = 0 until 2
        return classesRange.map { classIndex ->
            val ruleStats = jRip.getRuleStats(classIndex)
            val rules = ruleStats.ruleset
            rules
                    .filter { it.hasAntds() } //TODO: what to do with default rule?
                    .map { ruleToListOfNodes(it, predicatesByName, target, database) }
                    .flatten()
        }.flatten()
    }

    private fun <V> ruleToListOfNodes(
            it: weka.classifiers.rules.Rule?,
            predicatesByName: Map<String, Predicate<V>>,
            target: Predicate<V>,
            database: List<V>
    ): List<FishboneMiner.Node<V>> {
        val rule = it as JRip.RipperRule
        val conditions = rule.antds
                .filter { predicatesByName.containsKey(it.attr.name()) }
                .map {
                    val predicate = predicatesByName.getValue(it.attr.name())
                    if (it.attrValue.toInt() == 0) predicate.not() else predicate
                }

        return listOfNodes(conditions, target, database)
    }

    private fun rulesetString(jRip: JRip, instances: Instances): String {
        val classAttribute = instances.attribute(instances.numAttributes() - 1)
        val classesRange = 0 until 2
        return classesRange.joinToString(separator = ",\n") { classIndex ->
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