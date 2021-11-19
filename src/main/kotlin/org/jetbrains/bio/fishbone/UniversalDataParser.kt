package org.jetbrains.bio.fishbone

import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jetbrains.bio.fishbone.predicate.OverlapSamplePredicate
import org.jetbrains.bio.util.parse
import org.jetbrains.bio.util.toPath
import org.nield.kotlinstatistics.percentile
import org.nield.kotlinstatistics.range.OpenRange
import java.io.File
import java.nio.file.Files

class UniversalDataParser(
    private val dataFilename: String,
    private val outputFolder: String,
    private val youngAgeRange: OpenRange<Int>,
    private val oldAgeRange: OpenRange<Int>
) {

    private val idMap = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5")

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            OptionParser().apply {
                accepts("dataFilename", "Filename of input data file")
                    .withRequiredArg().ofType(String::class.java)
                accepts("outputFolder", "Folder for result files")
                    .withRequiredArg().ofType(String::class.java)
                accepts("youngMaxAge", "Max age limit for young cohort")
                    .withRequiredArg().ofType(String::class.java)
                accepts("oldMinAge", "Min age limit for old cohort")
                    .withRequiredArg().ofType(String::class.java)
                accepts("oldMaxAge", "Max age limit for old cohort")
                    .withRequiredArg().ofType(String::class.java)
                accepts("csvDelimiter", "Input csv data file delimiter")
                    .withRequiredArg().ofType(String::class.java)
                accepts("idColumnName", "Name of id column")
                    .withRequiredArg().ofType(String::class.java)
                accepts("ageColumnName", "Name of age column")
                    .withRequiredArg().ofType(String::class.java)
                accepts("sexColumnName", "Name of sex column")
                    .withOptionalArg().ofType(String::class.java)
                accepts("sexValue", "Sex value for filtering")
                    .withOptionalArg().ofType(String::class.java)
                formatHelpWith(BuiltinHelpFormatter(200, 2))
            }.parse(args) { options ->
                val processor = UniversalDataParser(
                    options.valueOf("dataFilename").toString(),
                    options.valueOf("outputFolder").toString(),
                    OpenRange(
                        0,
                        options.valueOf("youngMaxAge").toString().toInt()
                    ),
                    OpenRange(
                        options.valueOf("oldMinAge").toString().toInt(),
                        options.valueOf("oldMaxAge").toString().toInt()
                    )
                )

                val (database, predicates) = processor.createPredicatesFromData(
                    options.valueOf("csvDelimiter").toString()[0],
                    options.valueOf("idColumnName").toString(),
                    options.valueOf("ageColumnName").toString(),
                    options.valueOf("sexColumnName")?.toString(),
                    options.valueOf("sexValue")?.toString()
                )

                File(options.valueOf("outputFolder").toString()).mkdirs()
                processor.saveDatabaseToFile(database)
                processor.savePredicatesToFiles(predicates)
            }
        }
    }

    fun createPredicatesFromData(
        csvDelimiter: Char, idColumnName: String, ageColumnName: String,
        sexColumnName: String?, sexValue: String?
    ):
            Pair<List<Int>, List<OverlapSamplePredicate>> {
        val isInRange: (String) -> Boolean = { sample_value -> sample_value.toInt() in oldAgeRange }
        val dataReader = Files.newBufferedReader(dataFilename.toPath())
        val table = CSVParser(dataReader!!, CSVFormat.DEFAULT.withDelimiter(csvDelimiter))
            .map { csvRecord -> csvRecord }
        val header = table[0]
        val idColumnIndex = header.indexOf(idColumnName)
        val ageColumnIndex = header.indexOf(ageColumnName)
        val sexColumnIndex = if (sexColumnName == null) -1 else header.indexOf(sexColumnName)
        val data = table.filterIndexed { index, sample ->
            index != 0 &&
                    (sample[ageColumnIndex].toInt() in oldAgeRange ||
                            sample[ageColumnIndex].toInt() in youngAgeRange) &&
                    (sexColumnIndex == -1 || sample[sexColumnIndex] == sexValue)
        }.map {
            val group = it[idColumnIndex][0].toString()
            listOf(it[idColumnIndex].replace(group, idMap.getOrDefault(group, group))) +
                    it.toList().subList(1, it.size())
        }
        val database = data.map { it[idColumnIndex].toInt() }

        val predicatesMap = header.filter {
            it != idColumnName && it != ageColumnName
                    && (sexColumnName == null || it != sexColumnName)
        }.map { column ->
            val index = header.indexOf(column)
            val naValues = data.filterNot { it[index] != null && it[index] != "" && it[index] != "NA" }
                .map { it[idColumnIndex].toInt() }
            val samples = data.filter { it[index] != null && it[index] != "" && it[index] != "NA" }
            (1..100).map { percent ->
                val threshold = samples.map { it[index].toDouble() }.percentile(percent.toDouble())
                val fitSamples = samples.filter { it[index].toDouble() < threshold }.map { it[idColumnIndex].toInt() }

                "${column}_below_$percent" to Pair(fitSamples, naValues)
            }.distinctBy { it.second }.toMap()
        }.fold(emptyMap<String, Pair<List<Int>, List<Int>>>(), { map, t -> map + t }) +
                mapOf("${ageColumnName}_is_old" to (data.filter { isInRange(it[ageColumnIndex]) }
                    .map { it[idColumnIndex].toInt() } to emptyList()))
        val predicates = createOverlapSamplePredicates(predicatesMap, database)

        return Pair(database, predicates)
    }

    private fun createOverlapSamplePredicates(
        predicates: Map<String, Pair<List<Int>, List<Int>>>, database: List<Int>
    ): List<OverlapSamplePredicate> {
        return predicates.map {
            val samples = it.value.first
            val naSamples = it.value.second
            val notSamples = database.subtract(samples + naSamples).toList()
            OverlapSamplePredicate(it.key, samples, notSamples)
        }
    }

    private fun saveDatabaseToFile(database: List<Int>) {
        val databaseOutput = File("$outputFolder/database.txt")
        databaseOutput.createNewFile()
        databaseOutput.printWriter().use { out ->
            database.forEach { out.println(it) }
        }
    }

    private fun savePredicatesToFiles(predicates: List<OverlapSamplePredicate>) {
        predicates.map { predicate ->
            val output = File("$outputFolder/${predicate.name()}")
            output.printWriter().use { out ->
                predicate.samples.forEach { out.println(it) }
                predicate.notSamples.forEach { out.println("not: $it") }
            }
            output.absolutePath
        }
    }
}