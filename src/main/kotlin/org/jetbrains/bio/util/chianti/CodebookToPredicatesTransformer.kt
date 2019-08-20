package org.jetbrains.bio.util.chianti

import org.jetbrains.bio.util.chianti.LaboratoryDataParser.Companion.oldAgeRange
import org.jetbrains.bio.util.chianti.model.Codebook
import org.jetbrains.bio.util.chianti.model.DateVariable
import org.jetbrains.bio.util.chianti.model.EncodedVariable
import org.jetbrains.bio.util.chianti.model.NumericVariable

class CodebookToPredicatesTransformer(private val codebook: Codebook) {

    val predicates: Map<String, (Any) -> Boolean> = createPredicates()

    private fun createPredicates(): Map<String, (Any) -> Boolean> {
        return codebook.variables
                .map { (_, variable) ->
                    if (variable.name != "AGEL") {
                        when (variable) {
                            is NumericVariable -> variable.getPredicates()
                            is DateVariable -> variable.getPredicates()
                            is EncodedVariable -> variable.getPredicates()
                            else -> throw IllegalArgumentException("Unsupported variable type: $variable")
                        } as Map<String, (Any) -> Boolean>
                    } else {
                        val isInRange: (String) -> Boolean = { sample_value -> sample_value.toDouble() in oldAgeRange }
                        mapOf("AGEL_is_old" to isInRange) as Map<String, (Any) -> Boolean>
                    }
                }
                .fold(emptyMap(), { map, t -> map + t })
    }

}