package org.jetbrains.bio.util.chianti.codebook

import org.jetbrains.bio.util.chianti.variable.CombinedFeature
import org.jetbrains.bio.util.chianti.parser.DataParser.Companion.AGE_COLUMN
import org.jetbrains.bio.util.chianti.variable.Reference
import org.jetbrains.bio.util.chianti.variable.DateVariable
import org.jetbrains.bio.util.chianti.variable.NominativeVariable
import org.jetbrains.bio.util.chianti.variable.NumericVariable

class CodebookToPredicatesTransformer(private val codebook: Codebook, private val references: List<Reference>) {

    val predicates: Map<String, (Any) -> Boolean> = createPredicates()

    private fun createPredicates(): Map<String, (Any) -> Boolean> {
        val combinedFeaturePredicates = CombinedFeature.values()
                .filter { references.find { r -> r.name.replace("_female", "").replace("_male", "") == it.name } != null }
                .map {
                    references.find { r -> r.name == it.name }!!.getPredicates() as Map<String, (Any) -> Boolean>
                }
                .fold(emptyMap<String, (Any) -> Boolean>(), { map, t -> map + t })
        return codebook.variables.map { (_, variable) ->
            if (variable.name == AGE_COLUMN) {
                val isInRange: (String) -> Boolean = { sample_value -> sample_value.toDouble() in 65.0..75.0 }
                mapOf("${AGE_COLUMN}_is_old" to isInRange) as Map<String, (Any) -> Boolean>
            } else {
                val refs = references.filter {
                    it.name.replace("_female", "").replace("_male", "") == variable.name
                }
                if (refs.isNotEmpty()) {
                    refs.fold(emptyMap(), { map, t -> map + t.getPredicates() as Map<String, (Any) -> Boolean> })
                } else {
                    when (variable) {
                        is NumericVariable -> variable.getPredicates()
                        is DateVariable -> variable.getPredicates()
                        is NominativeVariable -> variable.getPredicates()
                        else -> throw IllegalArgumentException("Unsupported variable type: $variable")
                    } as Map<String, (Any) -> Boolean>
                }
            }
        }.fold(emptyMap<String, (Any) -> Boolean>(), { map, t -> map + t }) + combinedFeaturePredicates
    }

}