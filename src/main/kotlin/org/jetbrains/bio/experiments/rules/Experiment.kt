package org.jetbrains.bio.experiments.rules

import org.apache.log4j.Logger
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.FishboneMiner
import org.jetbrains.bio.rules.Miner
import org.jetbrains.bio.rules.RulesLogger
import org.jetbrains.bio.rules.validation.RuleSignificanceCheck
import org.jetbrains.bio.util.ExperimentHelper
import org.jetbrains.bio.util.div
import java.nio.file.Path

/**
 * Experiment class provides methods for data analysis.
 * Abstract parts are related to specific preprocessing steps of data for different experiment types.
 */
abstract class Experiment(private val outputFolder: String) {

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
        logger.info("Started run: $runName")

        val criterion = mineRulesRequest.criterion
        logger.info("Objective function to use: $criterion")

        return mineRulesRequest.miners
                .map { miningAlgorithm ->
                    logger.info("Processing $miningAlgorithm")

                    val miner = Miner.getMiner(miningAlgorithm)
                    val params = mapOf("objectiveFunction" to Miner.getObjectiveFunction<V>(criterion))
                    val minerResults = miner.mine(database, predicates, targets, ::predicateCheck, params)
                    miningAlgorithm to minerResults
                }
                .map { (miner, r) ->
                    val filteredRules = statisticalSignificant(miner, r, mineRulesRequest.significanceLevel, database)
                    miner to filteredRules
                }
                .map { (miner, r) ->
                    val outputPath = getOutputFilePath(miner, runName)
                    saveRulesToFile(r, criterion, criterion, outputPath)
                    logger.info("$miner rules saved to $outputPath")
                    miner to outputPath.toString().replace(".csv", ".json")
                }
                .toMap()
                .toMutableMap()
    }

    private fun <V> saveRulesToFile(rules: List<List<FishboneMiner.Node<V>>>, criterion: String, id: String, path: Path) {
        val rulesLogger = RulesLogger(path)
        rules.forEach { rulesLogger.log(id, it) }
        rulesLogger.done(criterion)
    }

    private fun <V> statisticalSignificant(
            miner: MiningAlgorithm,
            mineResults: List<List<FishboneMiner.Node<V>>>,
            significanceLevel: Double?,
            database: List<V>
    ): List<List<FishboneMiner.Node<V>>> {
        return if (significanceLevel != null) {
            val filteredMineResult = mineResults.map { rules ->
                if (miner == MiningAlgorithm.FISHBONE) {
                    // Filter out technical rule TRUE => target
                    val technicalRule = rules.last()
                    significantRules(rules.dropLast(1), significanceLevel, database) + technicalRule
                } else {
                    significantRules(rules, significanceLevel, database)
                }
            }
            logger.info("Significant rules P < $significanceLevel: " +
                    "${filteredMineResult.flatten().size} / ${mineResults.flatten().size}")
            filteredMineResult
        } else {
            logger.debug("Ignored significance testing")
            mineResults
        }
    }

    private fun <V> significantRules(
            rules: List<FishboneMiner.Node<V>>, significanceLevel: Double, database: List<V>
    ): List<FishboneMiner.Node<V>> {
        return rules.filter { RuleSignificanceCheck.test(it.rule, database) < significanceLevel }
    }

    private fun getOutputFilePath(miner: MiningAlgorithm, runName: String = ""): Path {
        return outputFolder / ("${runName}_${miner.label}_rules_${ExperimentHelper.timestamp()}.csv")
    }
}