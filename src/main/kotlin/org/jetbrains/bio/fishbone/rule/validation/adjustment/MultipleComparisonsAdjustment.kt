package org.jetbrains.bio.fishbone.rule.validation.adjustment

import org.jetbrains.bio.fishbone.miner.FishboneMiner

/**
 * Class represents adjustment abstract procedure for multiple comparisons problem.
 */
abstract class MultipleComparisonsAdjustment {
    abstract fun <T> test(pVals: List<Pair<FishboneMiner.Node<T>, Double>>, alpha: Double, m: Int): List<Pair<FishboneMiner.Node<T>, Boolean>>
}