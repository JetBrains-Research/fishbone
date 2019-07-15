package org.jetbrains.bio.experiments.rules

import org.apache.log4j.Logger
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.dataset.DataConfig
import org.jetbrains.bio.dataset.DataType
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.Rule
import org.jetbrains.bio.rules.RulesLogger
import org.jetbrains.bio.rules.FishboneMiner
import org.jetbrains.bio.rules.decisiontree.DecisionTreeMiner
import org.jetbrains.bio.rules.fpgrowth.FPGrowthMiner
import org.jetbrains.bio.rules.ripper.RipperMiner
import org.jetbrains.bio.rules.validation.ChiSquaredStatisticalSignificance
import org.jetbrains.bio.util.ExperimentHelper
import org.jetbrains.bio.util.div
import org.jetbrains.bio.util.toPath
import java.awt.Color
import java.nio.file.Path
import java.util.*

/**
 * Experiment class provides methods for data analysis.
 * Abstract parts are related to specific preprocessing steps of data for different experiment types.
 */
abstract class Experiment(private val outputFolder: String) {

    class PredicateInfo(val id: Int, val name: String, val satisfactionOnIds: BitSet)

    /**
     * Main method to run analysis on specified data files. Should be implemented in the following manner:
     * - parse mine request to get parameters
     * - prepare data files according to experiment type
     * - call [mine] method to get results
     */
    abstract fun run(mineRulesRequest: MineRulesRequest): Map<MiningAlgorithm, String>

    /**
     * Function to check predicate on database. Depends on experiment type
     */
    abstract fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean

    private val logger = Logger.getLogger(Experiment::class.java)

    /**
     * Run patterns mining according to mine request.
     */
    fun <V> mine(
            mineRulesRequest: MineRulesRequest,
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>> = emptyList()
    ): MutableMap<MiningAlgorithm, String> {
        val runName = mineRulesRequest.runName.orEmpty()

        return mineRulesRequest.miners
                .map { miningAlgorithm ->
                    logger.info("Processing $miningAlgorithm")
                    val miner = when (miningAlgorithm) {
                        MiningAlgorithm.FISHBONE -> FishboneMiner
                        MiningAlgorithm.RIPPER -> RipperMiner()
                        MiningAlgorithm.FP_GROWTH -> FPGrowthMiner()
                        MiningAlgorithm.DECISION_TREE -> DecisionTreeMiner()
                    }
                    val minerResults = miner.mine(
                            database,
                            predicates,
                            targets,
                            ::predicateCheck,
                            mapOf("objectiveFunction" to getObjectiveFunction<V>(mineRulesRequest.criterion))
                    )
                    miningAlgorithm to minerResults
                }
                .map { (miner, r) ->
                    val filteredRules = statisticalSignificant(r, mineRulesRequest.significanceLevel, database)
                    miner to filteredRules
                }
                .map { (miner, r) ->
                    val outputPath = getOutputFilePath(miner, runName)
                    saveRulesToFile(r, mineRulesRequest.criterion, miner, mineRulesRequest.criterion, outputPath)
                    miner to outputPath.toString().replace(".csv", ".json")
                }
                .toMap()
                .toMutableMap()
    }

    private fun <V> saveRulesToFile(
            result: List<List<FishboneMiner.Node<V>>>, criterion: String, miner: MiningAlgorithm, runName: String, outputPath: Path
    ) {
        val rulesLogger = RulesLogger(outputPath)

        result.forEach { rulesLogger.log(runName, it) }

        val rulesPath = rulesLogger.path.toString().replace(".csv", ".json").toPath()
        rulesLogger.done(rulesPath, generatePalette(), criterion)
        logger.info("$miner rules saved to $outputPath")
    }

    private fun <V> getObjectiveFunction(name: String): (Rule<V>) -> Double {
        logger.info("Fishbone algorithm will use $name")
        return when (name) {
            "conviction" -> Rule<V>::conviction
            "loe" -> Rule<V>::loe
            "correlation" -> Rule<V>::correlation
            else -> Rule<V>::conviction
        }
    }

    private fun <V> statisticalSignificant(
            mineResults: List<List<FishboneMiner.Node<V>>>,
            significanceLevel: Double?,
            database: List<V>
    ): List<List<FishboneMiner.Node<V>>> {
        return if (significanceLevel != null) {
            val filteredMineResult = mineResults.map { rules ->
                rules.filter {
                    ChiSquaredStatisticalSignificance.test(it.rule, database) < significanceLevel
                }
            }
            logger.info("Significant rules P < $significanceLevel: " +
                    "${filteredMineResult.flatten().size} / ${mineResults.flatten().size}")
            filteredMineResult
        } else {
            mineResults
        }
    }

    private fun getOutputFilePath(miner: MiningAlgorithm, runName: String = ""): Path {
        return outputFolder / ("${runName}_${miner.label}_rules_${ExperimentHelper.timestamp()}.csv")
    }

    private fun generatePalette(): (String) -> Color = { name ->
        val modification = modification(name)
        if (modification != null) {
            trackColor(modification)
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
    private fun trackColor(dataTypeId: String): Color {
        return when (dataTypeId.toLowerCase()) {
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
    }
}