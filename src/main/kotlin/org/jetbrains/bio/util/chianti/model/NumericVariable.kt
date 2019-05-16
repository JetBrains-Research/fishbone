package org.jetbrains.bio.util.chianti.model

class NumericVariable(
    name: String,
    meaning: String,
    val max: Double,
    val min: Double,
    val mean: Double,
    val median: Double,
    val q1: Double,
    val q3: Double
) :
    Variable(name, meaning) {

    fun getPredicates(): Map<String, (String) -> Boolean> {
        return mapOf(
            "low_$name" to isLowValue(),
            "normal_$name" to isNormalValue(),
            "high_$name" to isHighValue()
        )
    }

    private fun isLowValue() = { it: String -> it.toDouble() < q1 }
    private fun isHighValue() = { it: String -> it.toDouble() > q3 }
    private fun isNormalValue() = { it: String -> it.toDouble() in q1..q3 }

    // TODO: codebookcolumn should be changeble
    companion object {
        fun fromDataMap(data: Map<Int, List<String>>): NumericVariable {
            return NumericVariable(
                data.getValue(LaboCodebookColumn.Variable.index)[0],
                data.getValue(LaboCodebookColumn.Meaning.index)[0],
                data.getValue(LaboCodebookColumn.Max.index)[0].toDouble(),
                data.getValue(LaboCodebookColumn.Min.index)[0].toDouble(),
                data.getValue(LaboCodebookColumn.Mean.index)[0].toDouble(),
                data.getValue(LaboCodebookColumn.Median.index)[0].toDouble(),
                data.getValue(LaboCodebookColumn.Q1.index)[0].toDouble(),
                data.getValue(LaboCodebookColumn.Q3.index)[0].toDouble()
            )
        }
    }
}