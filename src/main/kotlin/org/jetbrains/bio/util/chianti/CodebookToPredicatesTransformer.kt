package org.jetbrains.bio.util.chianti

import org.jetbrains.bio.util.chianti.model.Codebook
import org.jetbrains.bio.util.chianti.model.DateVariable
import org.jetbrains.bio.util.chianti.model.EncodedVariable
import org.jetbrains.bio.util.chianti.model.NumericVariable

class CodebookToPredicatesTransformer(private val codebook: Codebook) {

    val predicates: Map<String, (Any) -> Boolean> = createPredicates()

    private fun createPredicates(): Map<String, (Any) -> Boolean> {
        val predicateList = codebook.variables.map { (_, variable) ->
            when (variable) {
                is NumericVariable -> variable.getPredicates()
                is DateVariable -> variable.getPredicates()
                is EncodedVariable -> variable.getPredicates()
                else -> throw IllegalArgumentException("Unsupported variable type: $variable")
            } as Map<String, (Any) -> Boolean>
        }
        val result = mutableMapOf<String, (Any) -> Boolean>()
        predicateList.forEach { p -> result.putAll(p) }
        return result
    }

}