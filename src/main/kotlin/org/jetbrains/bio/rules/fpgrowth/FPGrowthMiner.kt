package org.jetbrains.bio.rules.fpgrowth

import org.apache.log4j.Logger
import org.jetbrains.bio.experiments.rules.Experiment
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.Miner
import org.jetbrains.bio.rules.FishboneMiner
import smile.association.ARM
import smile.association.AssociationRule
import java.io.File
import java.util.*

/**
 * Miner run FP-growth algorithm on specified data
 * // TODO: replace with FP-growth from Weka! (not to use now)
 */
class FPGrowthMiner : Miner {
    private val associationRuleComparator = Comparator
            .comparingDouble(AssociationRule::support)
            .thenComparing(AssociationRule::confidence)!!
    private val logger = Logger.getLogger(FPGrowthMiner::class.java)
    private val fpGrowthRuleRegex = """\[.*?\] ==> \[.*?\]""".toRegex()

    override fun <V> mine(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
            params: Map<String, Any>
    ): List<List<FishboneMiner.Node<V>>> {
        try {
            logger.info("Processing fp-growth")

            val allPredicates = if (targets.isNotEmpty()) predicates + targets else predicates
            val predicatesInfo = getPredicatesInfoOverDatabase(allPredicates, database)
            val idsToNames = predicatesInfo.map { it.id to it.name }.toMap()
            val targetsIds = targets.mapNotNull { target -> predicatesInfo.find { it.name == target.name() }?.id }

            val items = database.withIndex().map { (idx, _) ->
                getSatisfiedPredicateIds(predicatesInfo, idx).toIntArray()
            }.toTypedArray()

            mine(items, idsToNames, targets = targetsIds)

            return emptyList()
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return emptyList()
        }
    }

    private fun getBestTargetFromFpGrowthResults(rulesFile: File): String {
        val rule = rulesFile.useLines { it.firstOrNull() }
        return fpGrowthRuleRegex.find(rule as CharSequence)!!.value.split(" ==> ")[1]
    }

    private fun <V> getPredicatesInfoOverDatabase(predicates: List<Predicate<V>>, database: List<V>)
            : List<Experiment.PredicateInfo> {
        return predicates.withIndex().map { (idx, predicate) ->
            // println("$idx out of ${predicates.size}")
            Experiment.PredicateInfo(idx, predicate.name(), predicate.test(database))
        }
    }

    private fun getSatisfiedPredicateIds(predicates: List<Experiment.PredicateInfo>, itemIdx: Int) =
            predicates.filter { it.satisfactionOnIds[itemIdx] }.map { it.id }

    /**
     * Method runs fp-growth algorithm, gets top predicates, writes them to file and returns path to this file
     */
    fun mine(
            items: Array<IntArray>,
            predicateIdsToNames: Map<Int, String>,
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

        // TODO: will be removed
        result.forEach { println(it) }

        return ""
    }

}