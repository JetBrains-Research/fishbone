package org.jetbrains.bio.rules.ripper

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
        fun mine(instances: Instances) {
            val jRip = JRip()
            jRip.buildClassifier(instances)

            val classAttribute = instances.attribute(instances.numAttributes() - 1)

            val rulesDescription = rulesetString(jRip, classAttribute, instances)

            println(rulesDescription)
        }

        private fun rulesetString(jRip: JRip, classAttribute: Attribute?, instances: Instances): String {
            return (0 until 2).map { classIndex ->
                val ruleStats = jRip.getRuleStats(classIndex)
                val rules = ruleStats.ruleset
                (0 until rules.size).map { ruleIndex ->
                    val rule = rules[ruleIndex]

                    val prefix = if (!rule.hasAntds()) "() " else ""
                    val ruleString = prefix + (rule as JRip.RipperRule).toString(classAttribute)

                    ruleString + "\n" + ruleStatString(ruleStats, ruleIndex, instances)
                }.joinToString(separator = ",\n")
            }.joinToString(separator = ",\n")
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
                    "recall: ${round(recall)}}"
        }

        private fun round(value: Double): String {
            return "%.4f".format(value)
        }
    }
}