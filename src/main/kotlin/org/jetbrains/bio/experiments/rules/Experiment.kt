package org.jetbrains.bio.experiments.rules

import org.apache.log4j.Logger
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.Miner
import org.jetbrains.bio.dataset.CellId
import org.jetbrains.bio.dataset.DataConfig
import org.jetbrains.bio.dataset.DataType
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.Rule
import org.jetbrains.bio.rules.RulesLogger
import org.jetbrains.bio.rules.RulesMiner
import org.jetbrains.bio.rules.decisiontree.DecionTreeMiner
import org.jetbrains.bio.rules.fpgrowth.FPGrowthMiner
import org.jetbrains.bio.rules.ripper.RipperMiner
import org.jetbrains.bio.rules.validation.ChiSquaredStatisticalSignificance
import org.jetbrains.bio.util.div
import org.jetbrains.bio.util.toPath
import smile.data.NumericAttribute
import weka.core.Attribute
import weka.core.Instances
import weka.core.SparseInstance
import java.awt.Color
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Future
import kotlin.collections.ArrayList

/**
 * Experiment class provides methods for data analysis.
 * Abstract parts are related to specific preprocessing steps of data for different experiment types.
 */
abstract class Experiment(private val outputFolder: String) {

    class PredicateInfo(val id: Int, val name: String, val satisfactionOnIds: BitSet)

    private val fpGrowthRuleRegex = """\[.*?\] ==> \[.*?\]""".toRegex()

    /**
     * Main method to run analysis on specified data files. Should be implemented in the following manner:
     * - parse mine request to get parameters
     * - prepare data files according to experiment type
     * - call [mine] method to get results
     */
    abstract fun run(mineRulesRequest: MineRulesRequest): Map<Miner, String>

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
            targets: List<Predicate<V>>? = null
    ): MutableMap<Miner, String> {
        val results = mineRulesRequest.miners
                .map { miner ->
                    logger.info("Processing $miner")
                    miner to when (miner) {
                        Miner.FISHBONE -> mineByFishbone(
                                database, predicates, targets?.getOrNull(0), mineRulesRequest.criterion, runName = mineRulesRequest.runName.orEmpty()
                        )
                        Miner.RIPPER -> mineByRipper(database, predicates, targets!!, runName = mineRulesRequest.runName.orEmpty())
                        Miner.FP_GROWTH -> mineByFPGrowth(database, predicates, targets?.getOrNull(0))
                        Miner.DECISION_TREE -> mineByDecisionTree(database, predicates, targets)
                    }
                }
                .map { (miner, r) ->
                    val filteredRules = statisticalSignificant(r.second, mineRulesRequest.checkSignificance, database)
                    Pair(miner, Pair(r.first, filteredRules))
                }
                .map { (miner, r) ->
                    saveRulesToFile(r, mineRulesRequest.criterion, miner, mineRulesRequest.criterion)
                    Pair(miner, r.first.replace(".csv", ".json"))
                }
                .toMap()
                .toMutableMap()

        // Decision tree is the only algorithm, which needs target explicitly
        // TODO: simplify
        return if (targets != null) results else addDecisionTreeResults(
                mineRulesRequest,
                database,
                predicates,
                results
        )
    }

    private fun <V> saveRulesToFile(
            result: Pair<String, List<List<RulesMiner.Node<V>>>>, criterion: String, miner: Miner, runName: String
    ) {
        val outputPath = Paths.get(result.first)
        if (miner.isFishboneSupported) { //TOdO: will be removed when other miner return the same type of result
            val rulesLogger = RulesLogger(outputPath)

            result.second.forEach { rulesLogger.log(runName, it) }

            val rulesPath = rulesLogger.path.toString().replace(".csv", ".json").toPath()
            rulesLogger.done(rulesPath, generatePalette(), criterion)
        }
        logger.info("$miner rules saved to $outputPath")
    }

    private fun <V> getInformationFunctionByName(name: String): (Rule<V>) -> Double {
        logger.info("Fishbone algorithm will use $name")
        return when (name) {
            "conviction" -> Rule<V>::conviction
            "loe" -> Rule<V>::loe
            "correlation" -> Rule<V>::correlation
            else -> Rule<V>::conviction
        }
    }

    private fun <V> mineByFishbone(
            database: List<V>,
            predicates: List<Predicate<V>>,
            target: Predicate<V>? = null,
            criterionName: String,
            maxComplexity: Int = 6,
            runName: String = ""
    ): Pair<String, List<Future<List<RulesMiner.Node<V>>>>> {
        try {
            logger.info("Processing fishbone")

            val sourcesToTargets = if (target != null) {
                listOf(predicates to target)
            } else {
                mapAllPredicatesToAll(predicates, predicates.withIndex())
            }

            val mineResults = RulesMiner.mine(
                    "All => All",
                    database,
                    sourcesToTargets,
                    { },
                    maxComplexity,
                    function = getInformationFunctionByName(criterionName),
                    or = true,
                    negate = true
            )

            return Pair(getOutputFilePath(Miner.FISHBONE, runName).toString(), mineResults)
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return Pair("", emptyList())
        }
    }

    private fun <V> mapAllPredicatesToAll(
            predicates: List<Predicate<V>>,
            indexedPredicates: Iterable<IndexedValue<Predicate<V>>>
    ): List<Pair<List<Predicate<V>>, Predicate<V>>> {
        return (0 until predicates.size).map { i ->
            val target = predicates[i]
            val sources = indexedPredicates.filter { (j, _) -> j != i }.map { (_, value) -> value }
            sources to target
        }.toList()
    }

    private fun <V> mineByRipper(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            runName: String = ""
    ): Pair<String, List<Future<List<RulesMiner.Node<V>>>>> {
        val instancesByTarget = targets.map { target ->
            val instances = createInstancesWithAttributesFromPredicates(target, predicates, database.size)
            addInstances(database, predicates + target, instances)
            Pair(target, instances)
        }.toMap()
        val result = RipperMiner.mine(
                instancesByTarget, (predicates + targets).map { Pair(it.name(), it) }.toMap(), database
        )

        return Pair(getOutputFilePath(Miner.RIPPER, runName).toString(), result)
    }

    private fun <V> createInstancesWithAttributesFromPredicates(
            target: Predicate<V>,
            predicates: List<Predicate<V>>,
            capacity: Int,
            name: String = timestamp()
    ): Instances {
        val classAttribute = Attribute(target.name(), listOf("1.0", "0.0"))
        val attributes = predicates.map { predicate -> Attribute(predicate.name()) } + classAttribute
        val instances = Instances(name, ArrayList(attributes), capacity)
        instances.setClassIndex(instances.numAttributes() - 1)
        return instances
    }

    private fun <V> addInstances(database: List<V>, predicates: List<Predicate<V>>, instances: Instances) {
        (0 until database.size).map { i ->
            val attributesValues = predicates.map { predicate ->
                if (predicateCheck(predicate, i, database)) 1.0 else 0.0
            }.toDoubleArray()
            instances.add(SparseInstance(1.0, attributesValues)) //TODO: type of instance?
        }
    }

    private fun <V> mineByFPGrowth(
            database: List<V>,
            predicates: List<Predicate<V>>,
            target: Predicate<V>? = null
    ): Pair<String, List<Future<List<RulesMiner.Node<V>>>>> {
        try {
            logger.info("Processing fp-growth")
            val rulesResults = getOutputFilePath(Miner.FP_GROWTH)

            val allPredicates = if (target != null) predicates + target else predicates
            val predicatesInfo = getPredicatesInfoOverDatabase(allPredicates, database)
            val idsToNames = predicatesInfo.map { it.id to it.name }.toMap()
            val targetId = predicatesInfo.find { it.name == target?.name() }?.id

            val items = database.withIndex().map { (idx, _) ->
                getSatisfiedPredicateIds(predicatesInfo, idx).toIntArray()
            }.toTypedArray()

            val rulesPath = FPGrowthMiner.mine(items, idsToNames, rulesResults, target = targetId)

            return Pair(rulesPath, emptyList())
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return Pair("", emptyList())
        }
    }

    private fun <V> getPredicatesInfoOverDatabase(predicates: List<Predicate<V>>, database: List<V>)
            : List<PredicateInfo> {
        return predicates.withIndex().map { (idx, predicate) ->
            // println("$idx out of ${predicates.size}")
            PredicateInfo(idx, predicate.name(), predicate.test(database))
        }
    }

    private fun getSatisfiedPredicateIds(predicates: List<PredicateInfo>, itemIdx: Int) =
            predicates.filter { it.satisfactionOnIds[itemIdx] }.map { it.id }

    private fun <V> addDecisionTreeResults(
            mineRulesRequest: MineRulesRequest,
            database: List<V>,
            predicates: List<Predicate<V>>,
            results: MutableMap<Miner, String>
    ): MutableMap<Miner, String> {
        if (mineRulesRequest.miners.contains(Miner.DECISION_TREE) && results.containsKey(Miner.FP_GROWTH)) {
            results[Miner.DECISION_TREE] = runDecisionTreeAlg(
                    database, predicates, results.getValue(Miner.FP_GROWTH)
            ).first
        }
        return results
    }

    // TODO: use all targets
    private fun <V> runDecisionTreeAlg(
            database: List<V>,
            predicates: List<Predicate<V>>,
            fpGrowthResultsFilename: String
    ): Pair<String, List<Future<List<RulesMiner.Node<V>>>>> {
        val fpGrowthResultsFile = File(fpGrowthResultsFilename)
        return if (fpGrowthResultsFile.canRead()) {
            val target = getBestTargetFromFpGrowthResults(fpGrowthResultsFile)
            val targetName = target.substring(1, target.length - 1)
            val targetPredicate = predicates.find { it.name() == targetName }
            val sourcePredicates = predicates.filter { it.name() != targetName }
            targetPredicate?.let { mineByDecisionTree(database, sourcePredicates, listOf(it)) }!! //TODO: check carefully
        } else {
            Pair("", emptyList())
        }
    }

    private fun getBestTargetFromFpGrowthResults(rulesFile: File): String {
        val rule = rulesFile.useLines { it.firstOrNull() }
        return fpGrowthRuleRegex.find(rule as CharSequence)!!.value.split(" ==> ")[1]
    }

    private fun <V> mineByDecisionTree(
            database: List<V>,
            sourcePredicates: List<Predicate<V>>,
            targets: List<Predicate<V>>? = null
    ): Pair<String, List<Future<List<RulesMiner.Node<V>>>>> {
        if (targets == null) {
            return Pair("", emptyList())
        }
        try {
            logger.info("Processing decision tree")
            val rulesResults = getOutputFilePath(Miner.DECISION_TREE)

            val attributes = sourcePredicates.map { NumericAttribute(it.name()) }.toTypedArray()
            val x = (0 until database.size).map { i ->
                sourcePredicates.map { predicate ->
                    if (predicateCheck(predicate, i, database)) 1.0 else 0.0
                }.toDoubleArray()
            }.toTypedArray()
            val targetPredicate = targets[0]
            val y = (0 until database.size).map { i ->
                if (predicateCheck(targetPredicate, i, database)) 1 else 0
            }.toIntArray()

            val rulesPath = DecionTreeMiner.mine(attributes, x, y, rulesResults)

            return Pair(rulesPath, emptyList())
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return Pair("", emptyList())
        }
    }

    private fun <V> statisticalSignificant(
            mineResults: List<Future<List<RulesMiner.Node<V>>>>,
            checkSignificance: Boolean,
            database: List<V>
    ): List<List<RulesMiner.Node<V>>> {
        val nonFilteredRules = mineResults.map {it.get()}
        val filteredMineResult = nonFilteredRules.map { rules ->
            if (checkSignificance) {
                rules.filter {
                    ChiSquaredStatisticalSignificance.test(it.rule, database) <
                            ChiSquaredStatisticalSignificance.SIGNIFICANCE_LEVEL
                }
            } else {
                rules
            }
        }
        logger.info("Significant rules P<${ChiSquaredStatisticalSignificance.SIGNIFICANCE_LEVEL}: " +
                "${filteredMineResult.flatten().size} / ${nonFilteredRules.flatten().size}")
        return filteredMineResult
    }

    private fun getOutputFilePath(miner: Miner, runName: String = ""): Path {
        val timestamp = timestamp()
        return outputFolder / ("${runName}_${miner.label}_rules_$timestamp.${miner.ext}")
    }

    private fun timestamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss"))

    private fun generatePalette(): (String) -> Color = { name ->
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