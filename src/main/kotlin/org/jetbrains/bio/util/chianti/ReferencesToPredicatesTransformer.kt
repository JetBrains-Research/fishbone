package org.jetbrains.bio.util.chianti

import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.util.chianti.model.ReferenceVariable

class ReferencesToPredicatesTransformer(private val references: List<CSVRecord>) {

    val predicates: Map<String, (Any) -> Boolean> = createPredicates()

    private fun createPredicates(): Map<String, (Any) -> Boolean> {
        val predicateList = references.map { reference ->
            ReferenceVariable.fromCSVRecord(reference).getPredicates() as Map<String, (Any) -> Boolean>
        }
        val result = mutableMapOf<String, (Any) -> Boolean>()
        predicateList.forEach { p -> result.putAll(p) }
        return result
    }

}