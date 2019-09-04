package org.jetbrains.bio.fishbone.miner

import org.slf4j.LoggerFactory
import org.jetbrains.bio.fishbone.predicate.Predicate
import weka.classifiers.rules.JRip
import weka.classifiers.rules.RuleStats
import weka.core.Instances

/**
 * This miner rus Ripper algorithm on specified data
 * {see: http://weka.sourceforge.net/doc.dev/weka/classifiers/rules/JRip.html}
 */
class RipperMiner : WekaMiner() {
    private val logger = LoggerFactory.getLogger(RipperMiner::class.java)

    override fun <V> mine(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
            params: Map<String, Any>
    ): List<List<FishboneMiner.Node<V>>> {
        try {
            if (targets.isEmpty()) {
                return emptyList()
            }

            val instancesByTarget = createInstances(database, predicates, targets, predicateCheck)
            val predicatesByName = (predicates + targets).associateBy { it.name() }

            return instancesByTarget.map { (target, instances) ->
                val jRip = JRip()
                jRip.buildClassifier(instances)

                logger.info(rulesetString(jRip, instances))

                buildRuleNodes(jRip, predicatesByName, target, database)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return emptyList()
        }
    }

    /**
     * This method creates string representation of mining result.
     * It is a customization of {@link weka.classifiers.rules.JRip.toString} without redundant ifo.
     */
    private fun rulesetString(jRip: JRip, instances: Instances): String {
        val classAttribute = instances.attribute(instances.numAttributes() - 1)
        // Iterate over classes to get sttistics for each of them (target vs not target)
        val classesRange = 0 until 2
        return classesRange.joinToString(separator = ",\n") { classIndex ->
            val ruleStats = jRip.getRuleStats(classIndex)
            val rules = ruleStats.ruleset
            // Get string representations for rules
            (0 until rules.size).joinToString(separator = ",\n") { ruleIndex ->
                val rule = rules[ruleIndex]

                val prefix = if (!rule.hasAntds()) "() " else ""
                val ruleString = prefix + (rule as JRip.RipperRule).toString(classAttribute)

                ruleString + "\n" + ruleStatString(ruleStats, ruleIndex, instances)
            }
        }
    }

    /**
     * Calculate basic rule statistics based on Weka output.
     */
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

    /**
     * Represent Ripper results in a form of Fishbone graph.
     * NOTE: cannot be abstract function of WekaMiner class because of different hierarchy of Weka algorithms
     */
    private fun <V> buildRuleNodes(
            jRip: JRip, predicatesByName: Map<String, Predicate<V>>, target: Predicate<V>, database: List<V>
    ): List<FishboneMiner.Node<V>> {
        val classesRange = 0 until 2
        return classesRange.map { classIndex ->
            val ruleStats = jRip.getRuleStats(classIndex)
            val rules = ruleStats.ruleset
            rules
                    .filter { it.hasAntds() }
                    .map { ruleToListOfNodes(it, predicatesByName, target, database) }
                    .flatten()
        }.flatten()
    }

    /**
     * Create a list of fishbone nodes for the rule
     */
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

    private fun round(value: Double): String {
        return "%.4f".format(value)
    }
}