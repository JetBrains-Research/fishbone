package org.jetbrains.bio.util.chianti.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DateVariable(name: String, meaning: String, val start: LocalDate, val end: LocalDate) : Variable(name, meaning) {

    fun getPredicates(
        date: LocalDate = start,
        predicates: Map<String, (LocalDate) -> Boolean> = emptyMap()
    ): Map<String, (LocalDate) -> Boolean> {
        if (date.isAfter(end)) {
            return predicates
        }
        val newDate = date.plusYears(10)
        val predicateName = name + "_between_" + date.toString() + "_and_" + newDate.toString()
        val predicate = predicateName to (isBetween(date, newDate))
        return getPredicates(newDate, predicates + predicate)
    }

    private fun isBetween(start: LocalDate, end: LocalDate) =
        { it: LocalDate -> (it.isAfter(start) || it.isEqual(start)) && (it.isBefore(end) || it.isEqual(end)) }

    companion object {
        fun fromDataMap(data: Map<Int, List<String>>): DateVariable {
            val dateRange = data.getValue(EpicCodebookColumn.Codes.index)[0].split("-")
            val start = LocalDate.parse(dateRange[0], DateTimeFormatter.ofPattern("ddMMMyyyy"))
            val end = LocalDate.parse(dateRange[1], DateTimeFormatter.ofPattern("ddMMMyyyy"))
            return DateVariable(
                data.getValue(EpicCodebookColumn.Variable.index)[0],
                data.getValue(EpicCodebookColumn.Meaning.index)[0],
                start,
                end
            )
        }
    }
}