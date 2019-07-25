package org.jetbrains.bio.rules.validation.adjustment

/**
 * Class represents adjustment abstract procedure for multiple comparisons problem.
 */
abstract class MultipleComparisonsAdjustment {
    abstract fun test(pVals: List<Double>, alpha: Double, m: Int): List<Boolean>
}