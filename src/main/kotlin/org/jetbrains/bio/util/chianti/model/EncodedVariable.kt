package org.jetbrains.bio.util.chianti.model

class EncodedVariable(name: String, meaning: String, val values: Set<String>) : Variable(name, meaning) {
    companion object {
        fun fromDataMap(data: Map<Int, List<String>>): EncodedVariable {
            return EncodedVariable(
                data.getValue(CodebookColumn.Variable.index)[0],
                data.getValue(CodebookColumn.Meaning.index)[0],
                data.getValue(CodebookColumn.Codes.index).toSet()
            )
        }
    }
}