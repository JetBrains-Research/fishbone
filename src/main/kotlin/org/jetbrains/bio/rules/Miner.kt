package org.jetbrains.bio.rules

import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.predicates.Predicate
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
    }
}