package org.jetbrains.bio.rules

import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.predicates.NotPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.predicates.TruePredicate
import org.jetbrains.bio.rules.decisiontree.DecisionTreeMiner
import org.jetbrains.bio.rules.fpgrowth.FPGrowthMiner
import org.jetbrains.bio.rules.ripper.RipperMiner

interface Miner {
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

        fun <V> heatmap(database: List<V>, target: Predicate<V>, rules: Collection<FishboneMiner.Node<V>>): HeatMap {
            return HeatMap.of(database, listOf(target) + rules.map { it.element }.filterNot { it is NotPredicate })
        }

        fun <V> upset(database: List<V>, target: Predicate<V>, rules: Collection<FishboneMiner.Node<V>>): Upset {
            return Upset.of(database, rules.map { it.element }.filterNot { it is NotPredicate }, target)
        }

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
                TargetAux(heatmap(database, target, singleRules), upset(database, target, singleRules))
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
            val parentNode = if (node.parent != null) newNode(node.parent, database, singleRules) else null
            if (parentNode == null) {
                val conditionPredicate = node.rule.conditionPredicate
                if (conditionPredicate !is TruePredicate && conditionPredicate.collectAtomics().size == 1) {
                    singleRules.add(node)
                }
            }
            val newNode = FishboneMiner.Node(newRule, node.element, parentNode)
            val ruleAux = RuleAux(
                    rule = Combinations.of(
                            database,
                            listOfNotNull(
                                    newNode.element,
                                    newNode.parent?.rule?.conditionPredicate,
                                    newNode.rule.targetPredicate
                            )
                    )
            )
            newNode.aux = ruleAux
            return newNode
        }
    }
}