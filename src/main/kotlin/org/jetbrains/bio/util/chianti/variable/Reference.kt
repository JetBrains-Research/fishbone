package org.jetbrains.bio.util.chianti.variable

class Reference(val name: String, val min: Double, val max: Double) {
    fun getPredicates(): Map<String, (String) -> Boolean> {
        return mapOf(
                "below_ref_$name" to isLowValue(),
                "inside_ref_$name" to isNormalValue(),
                "above_ref_$name" to isHighValue()
        )
    }

    private fun isLowValue() = { it: String -> it.toDouble() < min }
    private fun isHighValue() = { it: String -> it.toDouble() > max }
    private fun isNormalValue() = { it: String -> it.toDouble() in min..max }
}