package org.jetbrains.bio.util.chianti.model

import org.apache.commons.csv.CSVRecord

class ReferenceVariable(
    name: String,
    val min: Double,
    val max: Double
) :
    Variable(name, "") {

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

    companion object {
        fun fromCSVRecord(data: CSVRecord): ReferenceVariable =
                ReferenceVariable(data[0], data[1].toDouble(), data[3].toDouble())
    }
}