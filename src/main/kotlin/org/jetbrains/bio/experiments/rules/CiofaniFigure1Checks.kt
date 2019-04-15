package org.jetbrains.bio.experiments.rules

import org.apache.log4j.Logger
import org.jetbrains.bio.dataset.CellId
import org.jetbrains.bio.dataset.DataConfig
import org.jetbrains.bio.dataset.DataType
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.predicates.OverlapPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.RulesLogger
import org.jetbrains.bio.rules.RulesMiner
import org.jetbrains.bio.util.ciofani.CiofaniCheckQuery
import org.jetbrains.bio.util.ciofani.CiofaniTFsFileColumn
import org.jetbrains.bio.util.ciofani.CiofaniTFsOutputFileParser
import org.jetbrains.bio.util.ciofani.StatManager
import org.jetbrains.bio.util.div
import org.jetbrains.bio.util.toPath
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

class CiofaniFigure1Checks(private val databasePath: Path, private val sourceFilesUrls: List<String>,
                           private val targetFilesUrls: List<String>, private val outputFolder: String) {
    private fun createPredicates(genomeQuery: GenomeQuery, filesUrls: List<String>):
            CopyOnWriteArrayList<Predicate<Location>> {
        val result = CopyOnWriteArrayList<Predicate<Location>>()

        result.addAll(filesUrls.map { url ->
            val wcPeaks = LocationsMergingList.load(genomeQuery, Paths.get(url))
            OverlapPredicate(url.split(".")[0], wcPeaks)
        })

        return result
    }

    fun doCalculations() {
        LOG.info("Processing ${genomeQuery.id} $databasePath database")
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss"))
        val rulesResults = outputFolder / "rules_$timestamp.csv"

        val database = LocationsMergingList.load(genomeQuery, databasePath)

        val sourcePredicates = createPredicates(genomeQuery, sourceFilesUrls)
        val targetPredicates = createPredicates(genomeQuery, targetFilesUrls)
        val rulesLogger = RulesLogger(rulesResults)

        RulesMiner.mine("All => All @ $databasePath} ${genomeQuery.id}",
                database.toList(),
                targetPredicates.map { sourcePredicates to it },
                { rulesLogger.log("${genomeQuery.id}_$databasePath", it) }, 3)

        rulesLogger.done(rulesLogger.path.toString().replace(".csv", ".json").toPath(),
                generatePalette())
        LOG.info("Rules saved to $rulesResults")
    }

    companion object {
        private val LOG = Logger.getLogger(CiofaniFigure1Checks::class.java)

        val genomeQuery = GenomeQuery("mm10")
        private val allTfsToRorcQuery = CiofaniCheckQuery(
                mapOf(
                        CiofaniTFsFileColumn.TFS to fun(params): Boolean { return params[0] in listOf("Batf", "IRF4", "Maf", "Stat3") }
                ),
                Pair(
                        CiofaniTFsFileColumn.TFS, fun(params): Boolean { return params[0] == "RORC" }
                )
        )
        private val allTfsToIrf4Query = CiofaniCheckQuery(
                mapOf(
                        CiofaniTFsFileColumn.TFS to
                                fun(params): Boolean {
                                    return params[0] in listOf("Stat3", "Maf", "RORC", "Batf") && params[1].toDouble() >= params[2].toDouble()
                                }
                ),
                Pair(
                        CiofaniTFsFileColumn.TFS,
                        fun(params): Boolean {
                            return params[0] == "IRF4" && params[1].toDouble() >= params[2].toDouble()
                        }
                )
        )
        private val allTfsToIrf4QueryWithTr = CiofaniCheckQuery(
                mapOf(
                        CiofaniTFsFileColumn.TFS to
                                fun(params): Boolean {
                                    return params[0] in listOf("Stat3", "Maf", "RORC", "Batf") && params[1].toDouble() >= 2000
                                }
                ),
                Pair(
                        CiofaniTFsFileColumn.TFS,
                        fun(params): Boolean {
                            return params[0] == "IRF4" && params[1].toDouble() >= 2000
                        }
                )
        )


        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val query = allTfsToIrf4QueryWithTr

            val filePath = "/home/nlukashina/education/bioinf/spring/fishbone_materials/GSE40918_pCRMs_5TFs_th17.csv"
            val databaseFilename = "database.bed"
            val outputFolder = "ciofani_output"

            val pvalueStatistics = StatManager(filePath).getPvalueStatistics()
            print(pvalueStatistics)

            val sources = CiofaniTFsOutputFileParser.parseSources(filePath, query)
            val targets = CiofaniTFsOutputFileParser.parseTarget(filePath, query)
            val database = CiofaniTFsOutputFileParser.parseDatabase(filePath, databaseFilename)
            CiofaniFigure1Checks(database, sources, targets, outputFolder).doCalculations()
            cleanup(sources, targets, database)
        }

        private fun cleanup(sources: List<String>, targets: List<String>, database: Path) {
            sources.forEach { Files.delete(Paths.get(it)) }
            targets.forEach { Files.delete(Paths.get(it)) }
            Files.delete(database)
        }

        fun generatePalette(): (String) -> Color = { name ->
            val modification = modification(name)
            if (modification != null) {
                trackColor(modification, null)
            } else {
                Color.WHITE
            }
        }

        private fun modification(predicate: String, configuration: DataConfig? = null): String? {
            val m = "H3K\\d{1,2}(?:ac|me\\d)".toRegex(RegexOption.IGNORE_CASE).find(predicate) ?: return null
            if (configuration != null && m.value !in configuration.dataTypes()) {
                return null
            }
            return m.value
        }

        /**
         * Default colors by dataTypes
         */
        private fun trackColor(dataTypeId: String, cell: CellId? = null): Color {
            val color = when (dataTypeId.toLowerCase()) {
                "H3K27ac".toLowerCase() -> Color(255, 0, 0)
                "H3K27me3".toLowerCase() -> Color(153, 0, 255)
                "H3K4me1".toLowerCase() -> Color(255, 153, 0)
                "H3K4me3".toLowerCase() -> Color(51, 204, 51)
                "H3K36me3".toLowerCase() -> Color(0, 0, 204)
                "H3K9me3".toLowerCase() -> Color(255, 0, 255)
                DataType.METHYLATION.name.toLowerCase() -> Color.green
                DataType.TRANSCRIPTION.name.toLowerCase() -> Color.red
                else -> Color(0, 0, 128) /* IGV_DEFAULT_COLOR  */
            }
            return if (cell?.name == "OD") color.darker() else color
        }
    }
}