package org.jetbrains.bio.rules.fpgrowth

import org.jetbrains.bio.experiments.rules.Experiment
import smile.association.ARM
import smile.association.AssociationRule
import java.nio.file.Path
import java.util.*

class FPGrowthMiner {

    companion object {
        val directionFilter =
            { rule: AssociationRule, sources: List<Experiment.PredicateInfo>, targets: List<Experiment.PredicateInfo> ->
                rule.antecedent.all { ant -> sources.map { it.id }.contains(ant) } &&
                        rule.consequent.all { ant -> targets.map { it.id }.contains(ant) }
            }

        private val associationRuleComparator = Comparator
            .comparingDouble(AssociationRule::support)
            .thenComparing(AssociationRule::confidence)!!

        fun mine(
            items: Array<IntArray>,
            sources: List<Experiment.PredicateInfo>,
            sourceIdsToNames: Map<Int, String>,
            targets: List<Experiment.PredicateInfo>,
            targetIdsToNames: Map<Int, String>,
            outputFilePath: Path,
            minSupport: Int = 1,
            minConfidence: Double = 0.1,
            top: Int = 10
        ): String {
            val arm = ARM(items, minSupport)
            val rules = arm.learn(minConfidence)
            val result = rules
                .sortedWith(compareByDescending(associationRuleComparator) { v -> v })
                .filter { rule -> directionFilter(rule, sources, targets) }
                .take(top)
                .map { NamedAssociationRule(it, sourceIdsToNames, targetIdsToNames) }

            val outputFile = outputFilePath.toFile()
            outputFile.createNewFile()
            outputFile.printWriter().use { out -> result.forEach { out.println(it) } }

            return outputFile.absolutePath
        }

    }

}