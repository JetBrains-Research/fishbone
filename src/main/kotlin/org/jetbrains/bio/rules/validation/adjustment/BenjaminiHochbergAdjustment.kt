package org.jetbrains.bio.rules.validation.adjustment

/**
 * Benjamini-Hochberg procedure for multiple comparisons problem
 * (see: https://en.wikipedia.org/wiki/False_discovery_rate#Benjamini%E2%80%93Hochberg_procedure)
 */
class BenjaminiHochbergAdjustment {
    companion object : MultipleComparisonsAdjustment() {
        override fun test(pVals: List<Double>, alpha: Double, m: Int): List<Boolean> {
            val result = mutableListOf<Boolean>()
            (0 until pVals.size).forEach { i ->
                val level = (alpha * (i + 1)) / m
                if (pVals[i] >= level) {
                    result.add(false)
                } else {
                    return result + (i until pVals.size).map { true }
                }
            }
            return result
        }
    }
}