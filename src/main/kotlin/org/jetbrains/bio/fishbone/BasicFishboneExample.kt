package org.jetbrains.bio.fishbone

import joptsimple.OptionParser
import org.jetbrains.bio.experiment.Experiment
import org.jetbrains.bio.fishbone.api.MiningAlgorithm
import org.jetbrains.bio.fishbone.miner.Miner
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.PredicatesConstructor
import org.jetbrains.bio.fishbone.rule.RulesBoundedPriorityQueue
import org.jetbrains.bio.fishbone.rule.log.RulesLogger
import org.jetbrains.bio.util.*
import java.nio.file.Paths

class BasicFishboneExample(
    private val databaseUrl: String, private val sourceFolderUrl: String,
    private val targetFilesUrls: List<String>, private val criterion: String,
    private val outputFolder: String
) : Experiment("BasicFishboneExample") {

    fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(i as V)

    override fun doCalculations() {
        val sourceFilesUrls = sourceFolderUrl.toPath().list()
            .filter { it.isRegularFile && !it.name.startsWith(".") }
            .map { "${it.toAbsolutePath()}" }

        val databasePath = Paths.get(databaseUrl)
        val database = databasePath.toFile().useLines { outer -> outer.map { it.toInt() }.toList() }
        val predicates = PredicatesConstructor.createOverlapSamplePredicates(sourceFilesUrls)
        val targets = PredicatesConstructor.createOverlapSamplePredicates(targetFilesUrls)
        val objectiveFunction = Miner.getObjectiveFunction<Int>(criterion)
        val targetsResults =
            Miner.getMiner(MiningAlgorithm.FISHBONE).mine(
                database, predicates, targets, ::predicateCheck,
                mapOf(
                    "maxComplexity" to 2,
                    "topPerComplexity" to 100,
                    "objectiveFunction" to objectiveFunction
                )
            )
                .map { rules -> rules.distinctBy { it.rule } }
                .map { rules -> rules.sortedWith(RulesBoundedPriorityQueue.comparator(Miner.getObjectiveFunction(criterion))) }
                .flatten()

        val outputPath = outputFolder / ("LowLevelFishboneExample_rules_${Miner.timestamp()}.csv")
        val rulesLogger = RulesLogger(outputPath)
        rulesLogger.log("BasicFishboneExample", targetsResults)
        rulesLogger.done(criterion)
        print(outputPath.toString().replace(".csv", ".json"))
    }

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            OptionParser().apply {
                accepts("databaseUrl", "Database file url").withRequiredArg()
                accepts("sourceFolderUrl", "Source folder url").withRequiredArg()
                accepts("targetFilesUrls", "Target files urls").withRequiredArg()
                accepts("criterion", "Criterion").withRequiredArg()
                accepts("outputFolder", "Output folder").withRequiredArg()
            }.parse(args) { options ->
                BasicFishboneExample(
                    options.valueOf("databaseUrl").toString(),
                    options.valueOf("sourceFolderUrl").toString(),
                    options.valueOf("targetFilesUrls").toString().split(" "),
                    options.valueOf("criterion").toString(),
                    options.valueOf("outputFolder").toString()
                ).run()
            }
        }
    }
}
