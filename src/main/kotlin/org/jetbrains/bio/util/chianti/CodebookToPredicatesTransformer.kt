package org.jetbrains.bio.util.chianti

import org.jetbrains.bio.util.chianti.model.Codebook
import org.jetbrains.bio.util.chianti.model.DateVariable
import org.jetbrains.bio.util.chianti.model.EncodedVariable
import org.jetbrains.bio.util.chianti.model.NumericVariable
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.util.function.Predicate

class CodebookToPredicatesTransformer(private val codebook: Codebook) {

    val predicates: Map<String, (Any) -> Boolean> = createPredicates()

    private fun createPredicates(): Map<String, (Any) -> Boolean> {
        val predicateList = codebook.variables.map { (_, variable) ->
            when (variable) {
                is NumericVariable -> getPredicatesForNumericVariable(variable)
                is DateVariable -> getPredicatesForDateVariable(variable.name, variable.start, variable.end, emptyMap())
                is EncodedVariable -> createPredicatesForEncodedVariable(variable)
                else -> throw IllegalArgumentException("Unsupported variable type: $variable")
            } as Map<String, (Any) -> Boolean>
        }
        val result = mutableMapOf<String, (Any) -> Boolean>()
        predicateList.forEach { p -> result.putAll(p) }
        return result
    }

    private fun getPredicatesForNumericVariable(variable: NumericVariable): Map<String, (Double) -> Boolean> {
        return mapOf(
            "low_" + variable.name to NumericVariable.isLowValue(variable),
            "normal_" + variable.name to NumericVariable.isNormalValue(variable),
            "high_" + variable.name to NumericVariable.isHighValue(variable)
        )
    }

    private fun getPredicatesForDateVariable(
        variableName: String,
        date: LocalDate,
        end: LocalDate,
        predicates: Map<String, (LocalDate) -> Boolean>
    ): Map<String, (LocalDate) -> Boolean> {
        if (date.isAfter(end)) {
            return predicates
        }
        val newDate = date.plusYears(10)
        val predicateName = variableName + "_between_" + date.toString() + "_and_" + newDate.toString()
        val predicate = predicateName to (DateVariable.isBetween(date, newDate))
        return getPredicatesForDateVariable(variableName, newDate, end, predicates + predicate)
    }

    //TODO: miss encoded variables for now
    private fun createPredicatesForEncodedVariable(variable: EncodedVariable): Map<String, Predicate<String>> {
        return emptyMap()
    }

}