package org.jetbrains.bio.fishbone.rule.validation.adjustment

import org.jetbrains.bio.fishbone.miner.FishboneMiner

/**
 * Multiple comparisons with no adjustment.
 */
class NoAdjustment {
    companion object : MultipleComparisonsAdjustment() {
        override fun <T> test(pVals: List<Pair<FishboneMiner.Node<T>, Double>>, alpha: Double, m: Int): List<Pair<FishboneMiner.Node<T>, Boolean>> {
            return pVals.map { it.first to (it.second < alpha) }
        }

    }
}