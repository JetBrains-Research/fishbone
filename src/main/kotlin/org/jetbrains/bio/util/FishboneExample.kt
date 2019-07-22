package org.jetbrains.bio.util

import joptsimple.OptionParser
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import org.jetbrains.bio.genome.Genome
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.genome.downloadTo
import org.jetbrains.bio.predicate.OverlapPredicate
import org.jetbrains.bio.predicate.Predicate
import org.jetbrains.bio.miner.FishboneMiner
import org.jetbrains.bio.rule.RulesLogger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/*
Arguments example:
--databaseUrl
"https://artyomovlab.wustl.edu/publications/supp_materials/aging/rrbs/dmrs_filtered_ncyto_ge3_abs_avgdiff_ge0.025.bed"
--sourceFilesUrls
"https://artyomovlab.wustl.edu/publications/supp_materials/aging/chipseq/Y20O20/peaks/H3K4me1/span/consensus/H3K4me1_span_weak_consensus.bed https://artyomovlab.wustl.edu/publications/supp_materials/aging/chipseq/Y20O20/peaks/H3K4me3/span/consensus/H3K4me3_span_weak_consensus.bed https://artyomovlab.wustl.edu/publications/supp_materials/aging/chipseq/Y20O20/peaks/H3K27me3/span/consensus/H3K27me3_span_weak_consensus.bed https://artyomovlab.wustl.edu/publications/supp_materials/aging/chipseq/Y20O20/peaks/H3K36me3/span/consensus/H3K36me3_span_weak_consensus.bed https://artyomovlab.wustl.edu/publications/supp_materials/aging/chipseq/Y20O20/peaks/H3K27ac/span/consensus/H3K27ac_span_weak_consensus.bed https://artyomovlab.wustl.edu/publications/supp_materials/aging/rrbs/ucsc_cpgIslandExt.hg19.bed"
--targetFilesUrls
"https://artyomovlab.wustl.edu/publications/supp_materials/aging/rrbs/dmrs_filtered_ncyto_ge3_abs_avgdiff_ge0.025_down.bed https://artyomovlab.wustl.edu/publications/supp_materials/aging/rrbs/dmrs_filtered_ncyto_ge3_abs_avgdiff_ge0.025_up.bed"
--outputFolder
/rule_mining
 */
class FishboneExample(private val databaseUrl: String, private val sourceFilesUrls: List<String>,
                      private val targetFilesUrls: List<String>, private val outputFolder: String) {

    private fun createPredicates(genomeQuery: GenomeQuery, filesUrls: List<String>):
            CopyOnWriteArrayList<Predicate<Location>> {
        val result = CopyOnWriteArrayList<Predicate<Location>>()

        withTempDirectory("predicates") { tmpFolder ->
            result.addAll(filesUrls.map {
                val filePath = tmpFolder / FilenameUtils.getName(it)
                it.downloadTo(filePath)
                val wcPeaks = LocationsMergingList.load(genomeQuery, filePath)

                OverlapPredicate(filePath.name, wcPeaks)
            })
        }

        return result
    }

    fun doCalculations() {
        LOG.info("Processing ${genomeQuery.id} ${FilenameUtils.getName(databaseUrl)} database")
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss"))
        val rulesResults = outputFolder / "rules_$timestamp.csv"

        val database = withTempFile("database", ".bed") { tempFilePath ->
            databaseUrl.downloadTo(tempFilePath)
            LocationsMergingList.load(genomeQuery, tempFilePath)
        }

        val sourcePredicates = createPredicates(genomeQuery, sourceFilesUrls)
        val targetPredicates = createPredicates(genomeQuery, targetFilesUrls)
        val rulesLogger = RulesLogger(rulesResults)

        FishboneMiner.mine("All => All @ ${FilenameUtils.getName(databaseUrl)} ${genomeQuery.id}",
                database.toList(),
                targetPredicates.map { sourcePredicates to it },
                { rulesLogger.log("${genomeQuery.id}_${FilenameUtils.getName(databaseUrl)}", it) }, 3, buildClusters = true)

        rulesLogger.done("conviction")
        LOG.info("Rules saved to $rulesResults")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(FishboneExample::class.java)

        val genomeQuery = GenomeQuery(Genome["hg19"])

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            OptionParser().apply {
                accepts("databaseUrl", "Database file url").withRequiredArg()
                accepts("sourceFilesUrls", "Source files urls").withRequiredArg()
                accepts("targetFilesUrls", "Target files urls").withRequiredArg()
                accepts("outputFolder", "Output folder").withRequiredArg()
            }.parse(args) { options ->
                FishboneExample(
                        options.valueOf("databaseUrl").toString(),
                        options.valueOf("sourceFilesUrls").toString().split(" "),
                        options.valueOf("targetFilesUrls").toString().split(" "),
                        options.valueOf("outputFolder").toString()
                ).doCalculations()
            }
        }
    }
}
