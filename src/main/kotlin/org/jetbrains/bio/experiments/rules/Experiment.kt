package org.jetbrains.bio.experiments.rules

import org.apache.log4j.Logger
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.predicates.TruePredicate
import org.jetbrains.bio.rules.*
import org.jetbrains.bio.rules.sampling.SamplingStrategy
import org.jetbrains.bio.rules.validation.adjustment.BenjaminiHochbergAdjustment
import org.jetbrains.bio.rules.validation.adjustment.NoAdjustment
import org.jetbrains.bio.rules.validation.RuleSignificanceCheck
import org.jetbrains.bio.util.ExperimentHelper
import org.jetbrains.bio.util.div
import org.nield.kotlinstatistics.randomDistinct
import java.nio.file.Path

/**
 * Experiment class provides methods for data analysis.
 * Abstract parts are related to specific preprocessing steps of data for different experiment types.
 */
abstract class Experiment(val outputFolder: String) {

    companion object {
        private const val TOP_RULES = 10
        private const val EXPLORATORY_FRACTION = 0.5
        private const val N_SAMPLING = 50
        private val SAMPLING_STRATEGY = SamplingStrategy.NONE
        private const val ALPHA_HOLDOUT = 0.2
        private const val ALPHA_FULL = 0.2
    }

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
            request: MineRulesRequest,
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>> = emptyList()
    ): MutableMap<MiningAlgorithm, String> {
        val runName = request.runName.orEmpty()
        logger.info("Started run: $runName")

        val criterion = request.criterion
        logger.info("Objective function to use: $criterion")

        val topRules = request.topRules ?: TOP_RULES
        logger.info("Holdout approach will be used for $topRules top rules")

        val checkSignificance = (request.significanceLevel != null)
        val alphaExploratory = request.significanceLevel
        val alphaHoldout = if (checkSignificance) ALPHA_HOLDOUT else null
        val alphaFullDb = if (checkSignificance) ALPHA_FULL else null

        val targetsResults = targets
                .map { target ->
                    val bestExploredRules = (0 until N_SAMPLING)
                            .asSequence()
                            .map {
                                logger.info("Sampling $it out of $N_SAMPLING")
                                val strategy = request.sampling ?: SAMPLING_STRATEGY
                                if (checkSignificance) {
                                    splitDataset(database, target, strategy)
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
                            .map { (miner, rules) -> miner to rules.sortedWith(RulesBPQ.comparator(Miner.getObjectiveFunction(criterion))) }
                            .toList()

                    val significantRules = testRules(bestExploredRules, alphaFullDb, database)
                    updateRulesStatistics(significantRules, target, database)
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

    private fun <V> splitDataset(db: List<V>, target: Predicate<V>, strategy: SamplingStrategy): Pair<List<V>, List<V>> {
        val n = (EXPLORATORY_FRACTION * db.size).toInt()
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
                .map { (miner, rules) -> miner to (if (checkSignificance) rules.takeLast(topRules) else rules) }
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
                significantRules(rules.dropLast(1), alpha, db, adjust) + technicalRule
            } else {
                significantRules(rules, alpha, db, adjust)
            }
        } else {
            logger.debug("Ignored significance testing")
            rules
        }
    }

    private fun <V> significantRules(
            rules: List<FishboneMiner.Node<V>>, alpha: Double, db: List<V>, adjust: Boolean
    ): List<FishboneMiner.Node<V>> {
        val pVals = rules.map { node -> RuleSignificanceCheck.test(node.rule, db) }.sorted()
        val multipleComparisonResults = if (adjust) {
            BenjaminiHochbergAdjustment.test(pVals, alpha, rules.size)
        } else {
            NoAdjustment.test(pVals, alpha, rules.size)
        }
        val filteredRules = rules.withIndex()
                .filter { multipleComparisonResults[it.index] }
                .map { it.value }
        logger.info("Significant rules P < $alpha: ${filteredRules.size} / ${rules.size}")
        return filteredRules
    }


    private fun <V> testRules(
            rules: List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>>, alpha: Double?, db: List<V>
    ): List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>> {
        return rules.map { (miner, rules) -> miner to checkSignificance(miner, rules, alpha, db, true) }
    }

    private fun <V> updateRulesStatistics(
            significantRules: List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>>,
            target: Predicate<V>,
            database: List<V>
    ): List<Pair<MiningAlgorithm, List<FishboneMiner.Node<V>>>> {
        val singleRules = mutableListOf<FishboneMiner.Node<V>>()
        val updatedRules = significantRules
                .map { (miner, rules) ->
                    miner to rules.map { node ->
                        val conditionPredicate = node.rule.conditionPredicate
                        if (conditionPredicate !is TruePredicate && conditionPredicate.collectAtomics().size == 1) {
                            singleRules.add(node)
                        }
                        newNode(node, database)
                    }
                }
        val targetAux = if (singleRules.isNotEmpty()) {
            TargetAux(Miner.heatmap(database, target, singleRules), Miner.upset(database, target, singleRules))
        } else null
        return updatedRules.map { (miner, rules) ->
            miner to rules.map { node ->
                val conditionPredicate = node.rule.conditionPredicate
                if (conditionPredicate is TruePredicate) {
                    FishboneMiner.Node(node.rule, node.element, node.parent, targetAux)
                } else {
                    node
                }
            }
        }
    }

    private fun <V> newNode(node: FishboneMiner.Node<V>, database: List<V>): FishboneMiner.Node<V> {
        val newRule = Rule(node.rule.conditionPredicate, node.rule.targetPredicate, database)
        val parentNode = if (node.parent != null) newNode(node.parent, database) else null
        return FishboneMiner.Node(newRule, node.element, parentNode)
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