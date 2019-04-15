package org.jetbrains.bio.util.ciofani

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.nio.file.Files
import java.nio.file.Paths

class StatManager(private val filePath: String) {

    class PvalueStat(val max: Double, val min: Double, val mean: Double, val std: Double)

    fun getPvalueStatistics(): Map<String, PvalueStat> {
        Files.newBufferedReader(Paths.get(filePath)).use { reader ->
            return CSVParser(reader, CSVFormat.DEFAULT.withHeader())
                    .map { csvRecord -> getTfsFromRecord(csvRecord) }
                    .flatten()
                    .groupBy({ it.first }, { it.second })
                    .map { (tf, pvals) -> Pair(tf, PvalueStat(pvals.max()!!, pvals.min()!!, pvals.average(), std(pvals))) }
                    .toMap()
        }
    }

    private fun getTfsFromRecord(csvRecord: CSVRecord): List<Pair<String, Double>> {
        val tfs = csvRecord.get(CiofaniTFsFileColumn.TFS.columnName).split("_")
        return tfs
                .withIndex()
                .map { (idx, tf) ->
                    val pval = csvRecord.get(CiofaniTFsFileColumn.PVAL.columnName).split("_")[idx].toDouble()
                    Pair(tf, pval)
                }
    }

    // TODO: library
    private fun std(numArray: List<Double>): Double {
        var sum = 0.0
        var standardDeviation = 0.0

        for (num in numArray) {
            sum += num
        }

        val mean = sum / 10

        for (num in numArray) {
            standardDeviation += Math.pow(num - mean, 2.0)
        }

        return Math.sqrt(standardDeviation / 10)
    }
}