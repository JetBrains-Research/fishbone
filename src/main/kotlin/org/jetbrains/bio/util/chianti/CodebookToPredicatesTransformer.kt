package org.jetbrains.bio.util.chianti

import org.jetbrains.bio.util.chianti.model.Codebook
import org.jetbrains.bio.util.chianti.model.DateVariable
import org.jetbrains.bio.util.chianti.model.EncodedVariable
import org.jetbrains.bio.util.chianti.model.NumericVariable

class CodebookToPredicatesTransformer(private val codebook: Codebook) {

    val predicates: Map<String, (Any) -> Boolean> = createPredicates()

    private fun createPredicates(): Map<String, (Any) -> Boolean> {
        val predicateList = codebook.variables.map { (_, variable) ->
            if (variable.name != "X_AGEL") {
                when (variable) {
                    is NumericVariable -> variable.getPredicates()
                    is DateVariable -> variable.getPredicates()
                    is EncodedVariable -> {
                        variable.getPredicates()
                    }
                    else -> throw IllegalArgumentException("Unsupported variable type: $variable")
                } as Map<String, (Any) -> Boolean>
            } else {
                val oldAgeMin = 75
                val isInRange: (String) -> Boolean = { sample_value -> sample_value.toDouble() in 60.0..75.0 }
                mapOf("X_AGEL_is_old" to isInRange) as Map<String, (Any) -> Boolean>
            }
        }
        val result = mutableMapOf<String, (Any) -> Boolean>()
        predicateList.forEach { p -> result.putAll(p) }
        return result
    }

}