package org.jetbrains.bio.experiments.rules

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.*
import org.jetbrains.bio.rules.sampling.SamplingStrategy
import org.jetbrains.bio.rules.validation.RuleSignificanceCheck
import org.jetbrains.bio.util.ExperimentHelper
import org.jetbrains.bio.util.div
import org.nield.kotlinstatistics.randomDistinct
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Experiment class provides methods for data analysis.
 * Abstract parts are related to specific preprocessing steps of data for different experiment types.
 */
abstract class Experiment(val outputFolder: String) {

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

    private val logger = LoggerFactory.getLogger(Experiment::class.java)

    /**
     * Run patterns mining according to mine request.
     */
    fun <V> mine(
            request: MineRulesRequest,
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>> = emptyList()
    ): MutableMap<MiningAlgorithm, String> {
        val settings = request.settings

        val runName = request.runName.orEmpty()
        logger.info("Started run: $runName")

        val criterion = request.criterion
        logger.info("Objective function to use: $criterion")

        val topRules = request.topRules ?: settings.topRules
        logger.info("Holdout approach will be used for $topRules top rules")

        val checkSignificance = (request.significanceLevel != null)
        val alphaExploratory = request.significanceLevel
        val alphaHoldout = if (checkSignificance) settings.alphaHoldout else null
        val alphaFullDb = if (checkSignificance) settings.alphaFull else null

        val targetsResults = targets
                .map { target ->
                    val bestExploredRules = (0 until settings.nSampling)
                            .asSequence()
                            .map {
                                logger.info("Sampling ${it + 1} out of ${settings.nSampling}")
                                val strategy = request.sampling ?: settings.samplingStrategy
                                if (checkSignificance) {
                                    splitDataset(database, target, strategy, settings.exploratoryFraction)
                                } else (database to database)
                            }
                            .map { (exploratory, holdout) ->
                                val exploredRules = exploreRules(
                                        request.miners,
                                        exploratory,
                                        predicates,
                                        target,
                                        criterion,
                                        alphaExploratory,
                                        checkSignificance,
                                        topRules
                                )
                                testRules(exploredRules, alphaHoldout, holdout)
                            }
                            .flatten()
                            .fold(
                                    mapOf<MiningAlgorithm, List<FishboneMiner.Node<V>>>(),
                                    { m, (miner, rules) -> m + (miner to m.getOrDefault(miner, emptyList()) + rules) }
                            )
                            .map { (miner, rules) -> miner to rules.distinctBy { it.rule } }
                            .map { (miner, rules) ->
                                miner to rules.sortedWith(RulesBPQ.comparator(Miner.getObjectiveFunction(criterion)))
                            }
                            .toList()

                    val significantRules = testRules(bestExploredRules, alphaFullDb, database)
                    Miner.updateRulesStatistics(significantRules, target, database)
                }
                .flatten()
                .toList()

        return targetsResults
                .map { (miner, rules) ->
                    val outputPath = getOutputFilePath(miner, runName)
                    saveRulesToFile(rules, criterion, criterion, outputPath)
                    logger.info("$miner rules saved to $outputPath")
                    miner to outputPath.toString().replace(".csv", ".json")
                }
                .toMap()
                .toMutableMap()
    }

    private fun <V> splitDataset(
            db: List<V>, target: Predicate<V>, strategy: SamplingStrategy, exploratoryFraction: Double
    ): Pair<List<V>, List<V>> {
        val n = (exploratoryFraction * db.size).toInt()
        val exploratory = db.randomDistinct(n)
        val holdout = db.filter { it !in exploratory }

        return if (strategy != SamplingStrategy.NONE) {
            sample(exploratory, target, strategy) to sample(holdout, target, strategy)
        } else {
            exploratory to holdout
        }
    }

    private fun <V> sample(db: List<V>, target: Predicate<V>, strategy: SamplingStrategy): List<V> {
        val isTargetMajor = target.test(db).cardinality().toDouble() / db.size >= 0.5
        val majorClass = (if (isTargetMajor) target else target.not()).test(db)
        val majority = db.withIndex().filter { majorClass[it.index] }.map { it.value }
        val minority = db.subtract(majority).toList()

        return when (strategy) {
            SamplingStrategy.DOWNSAMPLING -> minority + majority.randomDistinct(minority.size)
            SamplingStrategy.UPSAMPLING -> majority + majority.map { minority.random() }
            else -> throw IllegalArgumentException("Unsupported strategy: $strategy")
        }
    }

    private fun <V> exploreRules(
            miners: Set<MiningAlgorithm>,
            db: List<V>,
            predicates: List<Predicate<V>>,
            target: Predicate<V>,
            criterion: String,
            alpha: Double?,
            checkSignificance: Boolean,
            topRules: Int
    ): List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>> {
        val objectiveFunction = Miner.getObjectiveFunction<V>(criterion)
        return miners
                .map { miningAlgorithm -> mine(db, predicates, target, miningAlgorithm, objectiveFunction) }
                .map { (miner, rules) -> miner to checkSignificance(miner, rules, alpha, db, false) }
                .map { (miner, rules) -> miner to rules.sortedWith(RulesBPQ.comparator(objectiveFunction)) }
                .map { (miner, rules) ->
                    val fakeRule = rules.last()
                    miner to (if (checkSignificance) rules.take(topRules) + fakeRule else rules)
                }
    }

    private fun <V> mine(
            data: List<V>,
            predicates: List<Predicate<V>>,
            target: Predicate<V>,
            algorithm: MiningAlgorithm,
            objectiveFunction: (Rule<V>) -> Double
    ): Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>> {
        logger.info("Processing $algorithm")
        val miner = Miner.getMiner(algorithm)
        val params = mapOf("objectiveFunction" to objectiveFunction)
        return algorithm to miner.mine(data, predicates, listOf(target), ::predicateCheck, params)[0]
    }

    private fun <V> checkSignificance(
            miner: MiningAlgorithm, rules: List<FishboneMiner.Node<V>>, alpha: Double?, db: List<V>, adjust: Boolean
    ): List<FishboneMiner.Node<V>> {
        return if (alpha != null) {
            if (miner == MiningAlgorithm.FISHBONE) {
                // Filter out technical rule TRUE => target
                val technicalRule = rules.last()
                RuleSignificanceCheck.significantRules(rules.dropLast(1), alpha, db, adjust) + technicalRule
            } else {
                RuleSignificanceCheck.significantRules(rules, alpha, db, adjust)
            }
        } else {
            logger.debug("Ignored significance testing")
            rules
        }
    }

    private fun <V> testRules(
            rules: List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>>, alpha: Double?, db: List<V>
    ): List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>> {
        return rules.map { (miner, rules) -> miner to checkSignificance(miner, rules, alpha, db, true) }
    }

    private fun <V> saveRulesToFile(rules: List<FishboneMiner.Node<V>>, criterion: String, id: String, path: Path) {
        val rulesLogger = RulesLogger(path)
        rulesLogger.log(id, rules)
        rulesLogger.done(criterion)
    }

    private fun getOutputFilePath(miner: MiningAlgorithm, runName: String = ""): Path {
        return outputFolder / ("${runName}_${miner.label}_rules_${ExperimentHelper.timestamp()}.csv")
    }
}