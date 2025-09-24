package org.jetbrains.bio.fishbone.miner

import org.jetbrains.bio.fishbone.api.MiningAlgorithm
import org.jetbrains.bio.fishbone.predicate.NotPredicate
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.TruePredicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.jetbrains.bio.fishbone.rule.visualize.Combinations
import org.jetbrains.bio.fishbone.rule.visualize.Heatmap
import org.jetbrains.bio.fishbone.rule.visualize.RuleVisualizeInfo
import org.jetbrains.bio.fishbone.rule.visualize.TargetVisualizeInfo
import org.jetbrains.bio.fishbone.rule.visualize.upset.Upset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface Miner {

    /**
     * Mine association rules from specified data.
     * @param database database
     * @param predicates list of predicates over database
     * @param targets list of targets over database
     * @param predicateCheck function to test predicate against element at specified position in database
     * @param params any other parameters to use, e.g. objective function
     *
     * @return list of mined rules per target
     */
    fun <V> mine(
        database: List<V>,
        predicates: List<Predicate<V>>,
        targets: List<Predicate<V>>,
        predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
        params: Map<String, Any>
    ): List<List<FishboneMiner.Node<V>>>

    companion object {
        fun <V> getObjectiveFunction(name: String): (Rule<V>) -> Double {
            return when (name) {
                "conviction" -> Rule<V>::conviction
                "loe" -> Rule<V>::loe
                "correlation" -> Rule<V>::correlation
                else -> Rule<V>::conviction
            }
        }

        fun getMiner(miningAlgorithm: MiningAlgorithm): Miner {
            return when (miningAlgorithm) {
                MiningAlgorithm.FISHBONE -> FishboneMiner
                MiningAlgorithm.RIPPER -> RipperMiner()
                MiningAlgorithm.FP_GROWTH -> FPGrowthMiner()
                MiningAlgorithm.DECISION_TREE -> DecisionTreeMiner()
            }
        }

        fun <V> heatmap(database: List<V>, target: Predicate<V>, rules: Collection<FishboneMiner.Node<V>>): Heatmap {
            return Heatmap.of(database, listOf(target) + rules.map { it.element }.filterNot { it is NotPredicate })
        }

        fun <V> upset(database: List<V>, target: Predicate<V>, rules: Collection<FishboneMiner.Node<V>>): Upset {
            return Upset.of(database, rules.map { it.element }.filterNot { it is NotPredicate }, target)
        }

        /**
         * Update statistics for rules, which were mined on one database, with correct values for another database.
         *
         * This method is useful for holdout approach {@see https://link.springer.com/article/10.1007/s10994-007-5006-x},
         * which is implemented in Experiment {@see org.jetbrains.bio.experiment.Experiment}
         *
         * @param rules list of rules to update
         * @param target target
         * @param database database to use to update rule statistics
         *
         * @return updated rules
         */
        fun <V> updateRulesStatistics(
            rules: List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>>,
            target: Predicate<V>,
            database: List<V>
        ): List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>> {
            var singleRules = mutableListOf<FishboneMiner.Node<V>>()
            val updatedRules = rules
                .map { (miner, rules) ->
                    miner to rules.map { node -> newNode(node, database, singleRules) }
                }
            singleRules = singleRules.distinctBy { it.rule }.toMutableList()
            val targetAux = if (singleRules.isNotEmpty()) {
                TargetVisualizeInfo(heatmap(database, target, singleRules), upset(database, target, singleRules))
            } else null
            return updatedRules.map { (miner, rules) ->
                miner to rules.map { node ->
                    val conditionPredicate = node.rule.conditionPredicate
                    if (conditionPredicate is TruePredicate) {
                        FishboneMiner.Node(node.rule, node.element, node.parent, targetAux)
                    } else {
                        node
                    }
                }
            }
        }

        private fun <V> newNode(
            node: FishboneMiner.Node<V>, database: List<V>, singleRules: MutableList<FishboneMiner.Node<V>>
        ): FishboneMiner.Node<V> {
            val newRule = Rule(node.rule.conditionPredicate, node.rule.targetPredicate, database)
            // Go up to the first-level rules
            val parentNode = if (node.parent != null) newNode(node.parent, database, singleRules) else null

            // If there is no parent, then it's a first-level rule -> save it to the list of single rules to build heatmap
            if (parentNode == null) {
                val conditionPredicate = node.rule.conditionPredicate
                if (conditionPredicate !is TruePredicate && conditionPredicate.collectAtomics().size == 1) {
                    singleRules.add(node)
                }
            }

            // Update statistics
            val newNode = FishboneMiner.Node(newRule, node.element, parentNode)
            val ruleAux = RuleVisualizeInfo(
                rule = Combinations.of(
                    database,
                    listOfNotNull(
                        newNode.element,
                        newNode.parent?.rule?.conditionPredicate,
                        newNode.rule.targetPredicate
                    )
                )
            )
            newNode.visualizeInfo = ruleAux

            return newNode
        }

        fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd__HH_mm_ss"))
    }
}