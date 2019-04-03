package org.jetbrains.bio.util.ciofani

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.big.BedEntry
import java.io.BufferedReader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


class CiofaniTFsOutputFileParser(private val filePath: String) {

    private val chrColumn = CiofaniTFsFileColumn.CHR.columnName
    private val startColumn = CiofaniTFsFileColumn.START.columnName
    private val endColumn = CiofaniTFsFileColumn.END.columnName

    fun parseDatabase(databaseFilename: String): Path {
        File(databaseFilename).printWriter().use { out ->
            Files.newBufferedReader(Paths.get(filePath)).use { reader ->
                CSVParser(reader, CSVFormat.DEFAULT.withHeader()).forEach { csvRecord ->
                    val bedEntryLine = bedEntryToString(
                            BedEntry(
                                    csvRecord.get(chrColumn),
                                    Integer.parseInt(csvRecord.get(startColumn)),
                                    Integer.parseInt(csvRecord.get(endColumn))
                            )
                    )
                    out.println(bedEntryLine)
                }
            }
        }
        return Paths.get(databaseFilename)
    }

    private fun parse(predicates: Map<CiofaniTFsFileColumn, (String) -> Boolean>, outputFilesPrefix: String = ""):
            List<String> {
        Files.newBufferedReader(Paths.get(filePath)).use { reader ->
            val bedEntries = createPredicateBedEntries(reader, predicates)
            return writePredicatesToFiles(bedEntries, outputFilesPrefix)
        }
    }

    private fun createPredicateBedEntries(
            inputFileReader: BufferedReader?, predicates: Map<CiofaniTFsFileColumn, (String) -> Boolean>
    ): Map<String, List<BedEntry>> {
        return CSVParser(inputFileReader, CSVFormat.DEFAULT.withHeader()).map { csvRecord ->
            getBedEntriesForRecord(csvRecord, predicates)
        }
                .flatten()
                .fold(mapOf()) { bedEntries, (predicate, bedEntry) ->
                    bedEntries.plus(Pair(predicate, bedEntries.getOrDefault(predicate, listOf()) + bedEntry))
                }
    }

    private fun getBedEntriesForRecord(csvRecord: CSVRecord, predicates: Map<CiofaniTFsFileColumn, (String) -> Boolean>):
            List<Pair<String, BedEntry>> {
        return predicates.map { (column, checkFunction) ->
            val predicateValues = csvRecord.get(column.columnName).split("_")
            predicateValues
                    .filter { value -> checkFunction(value) }
                    .map { value ->
                        Pair(
                                if (column.isValuePredicate) value else column.columnName,
                                BedEntry(
                                        csvRecord.get(chrColumn),
                                        Integer.parseInt(csvRecord.get(startColumn)),
                                        Integer.parseInt(csvRecord.get(endColumn))
                                )
                        )
                    }
        }.flatten()
    }

    private fun writePredicatesToFiles(bedEntries: Map<String, List<BedEntry>>, outputFilesPrefix: String): List<String> {
        return bedEntries.map { (predicate, entries) ->
            val predicateFilename = outputFilesPrefix + "_" + "$predicate.bed"
            File(predicateFilename).printWriter().use { out ->
                entries.forEach { out.println(bedEntryToString(it)) }
            }
            predicateFilename
        }
    }

    private fun bedEntryToString(entry: BedEntry): String {
        return "${entry.chrom}\t${entry.start}\t${entry.end}"
    }

    companion object {
        fun parseDatabase(filePath: String, databaseFilename: String): Path {
            return CiofaniTFsOutputFileParser(filePath).parseDatabase(databaseFilename)
        }

        fun parseSources(filePath: String, query: CiofaniCheckQuery): List<String> {
            return CiofaniTFsOutputFileParser(filePath).parse(query.sourcePredicates, "source")
        }

        fun parseTarget(filePath: String, query: CiofaniCheckQuery): List<String> {
            return CiofaniTFsOutputFileParser(filePath).parse(mapOf(query.targetPredicate), "target")
        }
    }
}
