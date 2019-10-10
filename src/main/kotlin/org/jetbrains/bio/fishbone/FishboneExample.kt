package org.jetbrains.bio.fishbone

import joptsimple.OptionParser
import org.jetbrains.bio.experiment.Experiment
import org.jetbrains.bio.fishbone.api.ExperimentSettings
import org.jetbrains.bio.fishbone.api.ExperimentType
import org.jetbrains.bio.fishbone.api.MineRulesRequest
import org.jetbrains.bio.fishbone.api.MiningAlgorithm
import org.jetbrains.bio.fishbone.experiment.FeaturesSetExperiment
import org.jetbrains.bio.util.*

class FishboneExample(private val databaseUrl: String, private val sourceFolderUrl: String,
                      private val targetFilesUrls: List<String>, private val outputFolder: String)
    : Experiment("FishboneExperiment") {

    override fun doCalculations() {
        val featuresSetExperiment = FeaturesSetExperiment(outputFolder)

        val sourceFilesUrls = sourceFolderUrl.toPath().list()
                .filter { it.isRegularFile && !it.name.startsWith(".") }
                .map { "${it.toAbsolutePath()}" }

        val mineRulesRequest = MineRulesRequest(ExperimentType.FEATURE_SET, "hg19", sourceFilesUrls,
                                                targetFilesUrls, databaseUrl, setOf(MiningAlgorithm.FISHBONE),
                                                "loe", 0.05, "FishboneExample",
                                                ExperimentSettings(nSampling = 150))
        featuresSetExperiment.run(mineRulesRequest)
    }

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            OptionParser().apply {
                accepts("databaseUrl", "Database file url").withRequiredArg()
                accepts("sourceFolderUrl", "Source folder url").withRequiredArg()
                accepts("targetFilesUrls", "Target files urls").withRequiredArg()
                accepts("outputFolder", "Output folder").withRequiredArg()
            }.parse(args) { options ->
                FishboneExample(
                        options.valueOf("databaseUrl").toString(),
                        options.valueOf("sourceFolderUrl").toString(),
                        options.valueOf("targetFilesUrls").toString().split(" "),
                        options.valueOf("outputFolder").toString()
                ).run()
            }
        }
    }
}
