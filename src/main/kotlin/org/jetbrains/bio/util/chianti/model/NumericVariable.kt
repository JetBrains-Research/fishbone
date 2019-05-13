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

    companion object {
        fun fromDataMap(data: Map<Int, List<String>>): NumericVariable {
            return NumericVariable(
                data.getValue(CodebookColumn.Variable.index)[0],
                data.getValue(CodebookColumn.Meaning.index)[0],
                data.getValue(CodebookColumn.Max.index)[0].toDouble(),
                data.getValue(CodebookColumn.Min.index)[0].toDouble(),
                data.getValue(CodebookColumn.Mean.index)[0].toDouble(),
                data.getValue(CodebookColumn.Median.index)[0].toDouble(),
                data.getValue(CodebookColumn.Q1.index)[0].toDouble(),
                data.getValue(CodebookColumn.Q3.index)[0].toDouble()
            )
        }

        fun isLowValue(variable: NumericVariable) = { it: Double -> it < variable.q1 }
        fun isHighValue(variable: NumericVariable) = { it: Double -> it > variable.q3 }
        fun isNormalValue(variable: NumericVariable) = { it: Double -> it <= variable.q3 && it >= variable.q1}
    }
}