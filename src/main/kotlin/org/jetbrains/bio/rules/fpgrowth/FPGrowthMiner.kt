package org.jetbrains.bio.rules.fpgrowth

import smile.association.ARM
import smile.association.AssociationRule
import java.nio.file.Path
import java.util.*

/**
 * Miner run FP-growth algorithm on specified data
 */
class FPGrowthMiner {

    companion object {

        private val associationRuleComparator = Comparator
            .comparingDouble(AssociationRule::support)
            .thenComparing(AssociationRule::confidence)!!

        /**
         * Method runs fp-growth algorithm, gets top predicates, writes them to file and returns path to this file
         */
        fun mine(
            items: Array<IntArray>,
            predicateIdsToNames: Map<Int, String>,
            outputFilePath: Path,
            minSupport: Int = 1,
            minConfidence: Double = 0.1,
            top: Int = 10,
            targets: List<Int> = emptyList()
        ): String {
            val arm = ARM(items, minSupport)
            val rules = arm.learn(minConfidence)
            val result = rules
                .asSequence()
                .sortedWith(compareByDescending(associationRuleComparator) { v -> v })
                .filter { rule -> rule.consequent.size == 1 }
                .filter { rule -> if (targets.isNotEmpty()) rule.consequent[0] in targets else true }
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