package org.jetbrains.bio.util.chianti.variable

import org.jetbrains.bio.util.chianti.codebook.EpicCodebookColumn

class NominativeVariable(name: String, meaning: String, val values: Set<String>) : Variable(name, meaning) {
    private val numeratedCodesRegex = """([0-9]\.|\.) [\w\W]*""".toRegex()
    private val rangeCodesRegex = """(\d+\-\d+)+ [\w]*""".toRegex()

    enum class NominativeVariableType {
        NUMERATED, // predicate = number
        RANGE, // predicate = range
        FREE_CODED // example: "USEDIA: No sediment code or Sediment coded: see lab assay methods" | predicate = none or string from sample
    }

    fun getPredicates(): Map<String, (String) -> Boolean> {
        val type = getType()
        return (when (type) {
            NominativeVariableType.NUMERATED -> values.map { value ->
                println(name)
                val code = """([0-9])\.""".toRegex().find(value)
                val idx = if (code != null) code.value.substringBefore(".") else ""
                val isEqualToIdx: (String) -> Boolean = { sample_value -> sample_value == idx }
                "${name}_is_$idx" to isEqualToIdx
            }.toMap()
            NominativeVariableType.RANGE -> values.map { value ->
                val predicateValue = """(\d+\-\d+)+""".toRegex().find(value)!!.value
                val range = predicateValue.split("-")
                val min = range[0].toDouble()
                val max = range[1].toDouble()
                val isInRange: (String) -> Boolean = { sample_value -> sample_value.toDouble() in min..max }
                "${name}_is_in_$predicateValue" to isInRange
            }.toMap()
            else -> emptyMap()
        })
    }

    fun getType(): NominativeVariableType {
        return when {
            values.all { value -> numeratedCodesRegex.matches(value) } -> NominativeVariableType.NUMERATED
            values.all { value -> rangeCodesRegex.containsMatchIn(value) } -> NominativeVariableType.RANGE
            else -> throw IllegalArgumentException("Unrecognized variable type $this")
        }
    }

    companion object {

        fun fromDataMap(data: Map<Int, List<String>>): NominativeVariable {
            return NominativeVariable(
                    data.getValue(EpicCodebookColumn.Variable.index)[0],
                    data.getValue(EpicCodebookColumn.Meaning.index)[0],
                    data.getValue(EpicCodebookColumn.Codes.index).toSet()
            )
        }
    }
}