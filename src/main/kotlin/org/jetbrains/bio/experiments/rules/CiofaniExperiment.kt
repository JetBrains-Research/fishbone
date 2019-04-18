package org.jetbrains.bio.experiments.rules

import org.apache.log4j.Logger
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.predicates.OverlapPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.RulesLogger
import org.jetbrains.bio.rules.RulesMiner
import org.jetbrains.bio.util.div
import org.jetbrains.bio.util.toPath
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

class CiofaniExperiment : Experiment {
    private val LOG = Logger.getLogger(CiofaniExperiment::class.java)
    private val genomeQuery = GenomeQuery("mm10")
    //TODO: output folder add as parameter
    private val defaultOutputFolder = "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_output"

    override fun run(mineRulesRequest: MineRulesRequest): String {
        return doCalculations(
            genomeQuery, mineRulesRequest.database, mineRulesRequest.sources, mineRulesRequest.targets
        )
    }

    private fun doCalculations(
        genomeQuery: GenomeQuery,
        databaseUrl: String,
        sourceFilesUrls: List<String>,
        targetFilesUrls: List<String>
    ): String {
        val databasePath = Paths.get(databaseUrl)
        LOG.info("Processing ${genomeQuery.id} $databasePath database")
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss"))
        val rulesResults = defaultOutputFolder / "rules_$timestamp.csv"

        val database = LocationsMergingList.load(genomeQuery, databasePath)

        val sourcePredicates = createPredicates(genomeQuery, sourceFilesUrls)
        val targetPredicates = createPredicates(genomeQuery, targetFilesUrls)
        val rulesLogger = RulesLogger(rulesResults)

        RulesMiner.mine(
            "All => All @ $databasePath} ${genomeQuery.id}",
            database.toList(),
            targetPredicates.map { sourcePredicates to it },
            { rulesLogger.log("${genomeQuery.id}_$databasePath", it) }, 3
        )

        val resultJson = rulesLogger.path.toString().replace(".csv", ".json").toPath()
        rulesLogger.done(resultJson, generatePalette())
        LOG.info("Rules saved to $rulesResults")

        return resultJson.toString()
    }

    private fun createPredicates(genomeQuery: GenomeQuery, filesUrls: List<String>):
            CopyOnWriteArrayList<Predicate<Location>> {
        val result = CopyOnWriteArrayList<Predicate<Location>>()

        result.addAll(filesUrls.map { url ->
            val wcPeaks = LocationsMergingList.load(genomeQuery, Paths.get(url))
            OverlapPredicate(url.split("/").last().split(".")[0], wcPeaks)
        })

        return result
    }
}