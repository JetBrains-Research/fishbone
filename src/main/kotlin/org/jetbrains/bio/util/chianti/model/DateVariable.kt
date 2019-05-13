package org.jetbrains.bio.util.chianti.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DateVariable(name: String, meaning: String, val start: LocalDate, val end: LocalDate) : Variable(name, meaning) {
    companion object {
        fun fromDataMap(data: Map<Int, List<String>>): DateVariable {
            val dateRange = data.getValue(CodebookColumn.Codes.index)[0].split("-")
            val start = LocalDate.parse(dateRange[0], DateTimeFormatter.ofPattern("ddMMMyyyy"))
            val end = LocalDate.parse(dateRange[1], DateTimeFormatter.ofPattern("ddMMMyyyy"))
            return DateVariable(
                data.getValue(CodebookColumn.Variable.index)[0],
                data.getValue(CodebookColumn.Meaning.index)[0],
                start,
                end
            )
        }

        fun isBetween(start: LocalDate, end: LocalDate) =
            { it: LocalDate -> (it.isAfter(start) || it.isEqual(start)) && (it.isBefore(end) || it.isEqual(end)) }
    }
}