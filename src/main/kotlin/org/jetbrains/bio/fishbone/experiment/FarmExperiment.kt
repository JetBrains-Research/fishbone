package org.jetbrains.bio.fishbone.experiment

import org.jetbrains.bio.fishbone.api.MineRulesRequest
import org.jetbrains.bio.fishbone.api.MiningAlgorithm
import org.jetbrains.bio.fishbone.api.SamplingStrategy
import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.miner.Miner
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.jetbrains.bio.fishbone.rule.RulesBoundedPriorityQueue
import org.jetbrains.bio.fishbone.rule.log.RulesLogger
import org.jetbrains.bio.fishbone.rule.validation.RuleImprovementCheck
import org.jetbrains.bio.util.div
import org.nield.kotlinstatistics.randomDistinct
import org.slf4j.LoggerFactory
import java.awt.Color
import java.nio.file.Path

/**
 * Experiment class provides methods for data analysis.
 * Abstract parts are related to specific preprocessing steps of data for different experiment types.
 */
abstract class FarmExperiment(val outputFolder: String) {

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

    private val logger = LoggerFactory.getLogger(FarmExperiment::class.java)

    /**
     * Run patterns mining according to mine request
     *
     * @param request request with mining parameters
     * @param database database
     * @param predicates list of predicates over database
     * @param targets list of targets over database
     *
     * @return map of paths to result files per mining algorithm
     */
    fun <V> mine(
        request: MineRulesRequest,
        database: List<V>,
        predicates: List<Predicate<V>>,
        targets: List<Predicate<V>> = emptyList()
    ): MutableMap<MiningAlgorithm, String> {
        val settings = request.settings

        val runName = request.runName
        logger.info("Started run: $runName")

        val criterion = request.criterion
        logger.info("Objective function to use: $criterion")

        val topRules = settings.topRules
        logger.info("Holdout approach will be used for $topRules top rules")

        val maxComplexity = settings.maxComplexity
        logger.info("Max complexity is set to $maxComplexity")

        val topPerComplexity = settings.topPerComplexity
        logger.info("Top complexity is set to $topPerComplexity")

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

                        // Split dataset into exploratory and holdout if we want to check stat.significance
                        if (checkSignificance) {
                            splitDataset(database, target, settings.samplingStrategy, settings.exploratoryFraction)
                        } else (database to database)
                    }
                    .map { (exploratory, holdout) ->
                        // Explore rules on exploratory dataset and test significance against holdout dataset
                        val exploredRules = exploreRules(
                            request.miners,
                            exploratory,
                            predicates,
                            target,
                            criterion,
                            alphaExploratory,
                            checkSignificance,
                            topRules,
                            maxComplexity,
                            topPerComplexity
                        )
                        testRules(exploredRules, alphaHoldout, holdout)
                    }
                    .flatten()
                    .fold(mapOf<MiningAlgorithm, List<FishboneMiner.Node<V>>>()) { m, (miner, rules) ->
                        m + (miner to m.getOrDefault(miner, emptyList()) + rules)
                    }
                    .map { (miner, rules) -> miner to rules.distinctBy { it.rule } }
                    .map { (miner, rules) -> miner to sortByObjectiveFunction(rules, criterion) }
                    .toList()

                val significantRules = testRules(bestExploredRules, alphaFullDb, database)
                Miner.updateRulesStatistics(significantRules, target, database)
            }
            .flatten()
            .toList()

        return targetsResults.associate { (miner, rules) ->
            val outputPath = getOutputFilePath(miner, runName)
            saveRulesToFile(rules, criterion, criterion, outputPath)
            logger.info("$miner rules saved to $outputPath")
            miner to outputPath.toString().replace(".csv", ".json")
        }.toMutableMap()
    }

    /**
     * Split dataset into exploratory and holdout
     * @param db database to split
     * @param target target (used in sampling)
     * @param strategy sampling strategy
     * @param exploratoryFraction fraction of exploratory holdout to maintain in split
     *
     * @return exploratory to holdout datasets
     */
    fun <V> splitDataset(
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

    /**
     * Sampling according sampling strategy
     *
     * @param db database to split
     * @param target target (used in sampling)
     * @param strategy sampling strategy
     *
     * @return sample
     */
    fun <V> sample(db: List<V>, target: Predicate<V>, strategy: SamplingStrategy): List<V> {
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

    /**
     * Mine rules over database
     *
     * @param miners mining algorithms to use
     * @param db database
     * @param predicates predicates
     * @param target target
     * @param criterion name of objective function to use
     * @param alpha significance level to use in significance pre-check
     * @param checkSignificance if we need to check significance
     * @param topRules # of rules (top by objective function) to maintain from mined rules
     *
     * @return mined rules by mining algorithm
     */
    private fun <V> exploreRules(
        miners: Set<MiningAlgorithm>,
        db: List<V>,
        predicates: List<Predicate<V>>,
        target: Predicate<V>,
        criterion: String,
        alpha: Double?,
        checkSignificance: Boolean,
        topRules: Int,
        maxComplexity: Int,
        topPerComplexity: Int
    ): List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>> {
        val objectiveFunction = Miner.getObjectiveFunction<V>(criterion)
        return miners
            .map { miningAlgorithm ->
                mine(
                    db, predicates, target, miningAlgorithm, objectiveFunction,
                    maxComplexity, topPerComplexity
                )
            }
            .map { (miner, rules) -> miner to getProductiveRules(miner, rules, alpha, db, false) }
            .map { (miner, rules) -> miner to sortByObjectiveFunction(rules, criterion) }
            .map { (miner, rules) ->
                // Technical rule TRUE => target from Fishbone algorithm
                val technicalRule = rules.last()
                miner to (if (checkSignificance) rules.take(topRules) + technicalRule else rules)
            }
    }

    /**
     * Mine rules by specified algorithm
     */
    private fun <V> mine(
        data: List<V>,
        predicates: List<Predicate<V>>,
        target: Predicate<V>,
        algorithm: MiningAlgorithm,
        objectiveFunction: (Rule<V>) -> Double,
        maxComplexity: Int,
        topPerComplexity: Int
    ): Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>> {
        logger.info("Processing $algorithm")
        val miner = Miner.getMiner(algorithm)
        val params = mapOf(
            "objectiveFunction" to objectiveFunction, "maxComplexity" to maxComplexity,
            "topPerComplexity" to topPerComplexity
        )
        val miningResult = miner.mine(data, predicates, listOf(target), ::predicateCheck, params)
        // Get results for the first target only
        return algorithm to miningResult[0]
    }

    /**
     * Check rules productivity over database.
     *
     * @param miner mining algorithm
     * @param rules to check
     * @param alpha significance level for statistical significance check
     * @param db database
     * @param adjust if we want to use multiple comparison adjustment
     *
     * @return productive rules only
     */
    fun <V> getProductiveRules(
        miner: MiningAlgorithm, rules: List<FishboneMiner.Node<V>>, alpha: Double?, db: List<V>, adjust: Boolean
    ): List<FishboneMiner.Node<V>> {
        return if (alpha != null) {
            if (miner == MiningAlgorithm.FISHBONE) {
                // Technical rule TRUE => target from Fishbone algorithm
                val technicalRule = rules.last()
                RuleImprovementCheck.productiveRules(rules.dropLast(1), alpha, db, adjust) + technicalRule
            } else {
                RuleImprovementCheck.productiveRules(rules, alpha, db, adjust)
            }
        } else {
            logger.debug("Ignored significance testing")
            rules
        }
    }

    private fun <V> testRules(
        rules: List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>>, alpha: Double?, db: List<V>
    ): List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>> {
        return rules.map { (miner, rules) -> miner to getProductiveRules(miner, rules, alpha, db, true) }
    }

    private fun <V> sortByObjectiveFunction(rules: List<FishboneMiner.Node<V>>, criterion: String) =
        rules.sortedWith(RulesBoundedPriorityQueue.comparator(Miner.getObjectiveFunction(criterion)))

    private fun <V> saveRulesToFile(rules: List<FishboneMiner.Node<V>>, criterion: String, id: String, path: Path) {
        val rulesLogger = RulesLogger(path)
        rulesLogger.log(id, rules)
        rulesLogger.save(criterion) { Color.WHITE }
    }

    private fun getOutputFilePath(miner: MiningAlgorithm, runName: String = ""): Path {
        return outputFolder / ("${runName}_${miner.label}_rules_${Miner.timestamp()}.csv")
    }
}