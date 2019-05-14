package org.jetbrains.bio.rules.fpgrowth

import smile.association.ARM
import smile.association.AssociationRule
import java.nio.file.Path
import java.util.*

class FPGrowthMiner {

    companion object {

        private val associationRuleComparator = Comparator
            .comparingDouble(AssociationRule::support)
            .thenComparing(AssociationRule::confidence)!!

        fun mine(
            items: Array<IntArray>,
            predicateIdsToNames: Map<Int, String>,
            outputFilePath: Path,
            minSupport: Int = 1,
            minConfidence: Double = 0.1,
            top: Int = 10,
            target: Int? = null
        ): String {
            val arm = ARM(items, minSupport)
            val rules = arm.learn(minConfidence)
            val result = rules
                .asSequence()
                .sortedWith(compareByDescending(associationRuleComparator) { v -> v })
                .filter { rule -> rule.consequent.size == 1 }
                .filter { rule -> if (target != null) rule.consequent[0] == target else true }
                .take(top)
                .map { NamedAssociationRule(it, predicateIdsToNames) }
                .toList()

            val outputFile = outputFilePath.toFile()
            outputFile.createNewFile()
            outputFile.printWriter().use { out -> result.forEach { out.println(it) } }

            return outputFile.absolutePath
        }

    }

}