package org.jetbrains.bio.rule.validation.adjustment

import org.jetbrains.bio.miner.FishboneMiner

/**
 * Benjamini-Hochberg procedure for multiple comparisons problem
 * (see: https://en.wikipedia.org/wiki/False_discovery_rate#Benjamini%E2%80%93Hochberg_procedure)
 */
class BenjaminiHochbergAdjustment {

    companion object : MultipleComparisonsAdjustment() {

        override fun <T> test(
                pVals: List<Pair<FishboneMiner.Node<T>, Double>>, alpha: Double, m: Int
        ): List<Pair<FishboneMiner.Node<T>, Boolean>> {
            val result = mutableListOf<Pair<FishboneMiner.Node<T>, Boolean>>()
            (0 until pVals.size).forEach { i ->
                val level = (alpha * (i + 1)) / m
                val p = pVals[i].second
                if (p >= level) {
                    result.add(pVals[i].first to false)
                } else {
                    return result + (i until pVals.size).map { pVals[it].first to true }
                }
            }
            return result
        }
    }
}