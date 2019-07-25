package org.jetbrains.bio.rules.validation.adjustment

/**
 * Multiple comparisons with no adjustment.
 */
class NoAdjustment {
    companion object : MultipleComparisonsAdjustment() {
        override fun test(pVals: List<Double>, alpha: Double, m: Int): List<Boolean> {
            return pVals.map { it < alpha }
        }

    }
}