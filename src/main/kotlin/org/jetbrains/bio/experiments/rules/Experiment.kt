package org.jetbrains.bio.experiments.rules

import org.apache.log4j.Logger
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.Miner
import org.jetbrains.bio.dataset.CellId
import org.jetbrains.bio.dataset.DataConfig
import org.jetbrains.bio.dataset.DataType
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.RulesLogger
import org.jetbrains.bio.rules.RulesMiner
import org.jetbrains.bio.rules.decisiontree.DecionTreeMiner
import org.jetbrains.bio.rules.fpgrowth.FPGrowthMiner
import org.jetbrains.bio.util.div
import org.jetbrains.bio.util.toPath
import smile.data.NumericAttribute
import java.awt.Color
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

abstract class Experiment(private val outputFolder: String) {

    class PredicateInfo(val id: Int, val name: String, val satisfactionOnIds: BitSet)

    private val fpGrowthRuleRegex = """\[.*?\] ==> \[.*?\]""".toRegex()

    abstract fun run(mineRulesRequest: MineRulesRequest): Map<Miner, String>
    abstract fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean

    private val logger = Logger.getLogger(Experiment::class.java)

    fun <V> mine(
        mineRulesRequest: MineRulesRequest,
        database: List<V>,
        predicates: List<Predicate<V>>,
        target: Predicate<V>? = null
    ): MutableMap<Miner, String> {
        val isTargetPresented = target != null
        val results = mineRulesRequest.miners.map { miner ->
            miner to when (miner) {
                Miner.FISHBONE -> mineByFishbone(database, predicates, target)
                Miner.FP_GROWTH -> mineByFPGrowth(database, predicates, target)
                Miner.DECISION_TREE -> if (isTargetPresented) mineByDecisionTree(database, predicates, target!!) else ""
            }
        }.toMap().toMutableMap()

        // Decision tree is the only algorithm, which needs target explicitly
        return if (isTargetPresented) results else addDecisionTreeResults(
            mineRulesRequest,
            database,
            predicates,
            results
        )
    }

    private fun <V> mineByFishbone(
        database: List<V>,
        predicates: List<Predicate<V>>,
        target: Predicate<V>? = null
    ): String {
        try {
            logger.info("Processing fishbone")
            val rulesResults = getOutpuFilePath(Miner.FISHBONE)
            val rulesLogger = RulesLogger(rulesResults)

            val indexedPredicates = predicates.withIndex()
            val sourcesToTargets = if (target != null) {
                listOf(predicates to target)
            } else {
                mapAllPredicatesToAll(predicates, indexedPredicates)
            }

            RulesMiner.mine(
                "All => All",
                database,
                sourcesToTargets,
                { rulesLogger.log("test", it) },
                3
            )

            val rulesPath = rulesLogger.path.toString().replace(".csv", ".json").toPath()
            rulesLogger.done(rulesPath, generatePalette())
            logger.info("Fishbone rules saved to $rulesResults")

            return rulesPath.toString()
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return ""
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

    private fun <V> mineByFPGrowth(
        database: List<V>,
        predicates: List<Predicate<V>>,
        target: Predicate<V>? = null
    ): String {
        try {
            logger.info("Processing fp-growth")
            val rulesResults = getOutpuFilePath(Miner.FP_GROWTH)

            val allPredicates = if (target != null) predicates + target else predicates
            val predicatesInfo = getPredicatesInfoOverDatabase(allPredicates, database)
            val idsToNames = predicatesInfo.map { it.id to it.name }.toMap()
            val targetId = predicatesInfo.find { it.name == target?.name() }?.id

            val items = database.withIndex().map { (idx, _) ->
                getSatisfiedPredicateIds(predicatesInfo, idx).toIntArray()
            }.toTypedArray()

            val rulesPath = FPGrowthMiner.mine(items, idsToNames, rulesResults, target = targetId)
            logger.info("FPGrowth rules saved to $rulesPath")

            return rulesPath
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return ""
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
        if (mineRulesRequest.miners.contains(Miner.DECISION_TREE) /*&& results.containsKey(Miner.FP_GROWTH)*/) {
            results[Miner.DECISION_TREE] = runDecisionTreeAlg(
                database, predicates, results.getValue(Miner.FP_GROWTH)
            )
        }
        return results
    }

    // TODO: use all targets
    private fun <V> runDecisionTreeAlg(
        database: List<V>,
        predicates: List<Predicate<V>>,
        fpGrowthResultsFilename: String
    ): String {
        val fpGrowthResultsFile = File(fpGrowthResultsFilename)
        return if (fpGrowthResultsFile.canRead()) {
            val target = getBestTargetFromFpGrowthResults(fpGrowthResultsFile)
            val targetName = target.substring(1, target.length - 1)
            val targetPredicate = predicates.find { it.name() == targetName }
            val sourcePredicates = predicates.filter { it.name() != targetName }
            targetPredicate?.let { mineByDecisionTree(database, sourcePredicates, it) }.orEmpty()
        } else {
            ""
        }
    }

    private fun getBestTargetFromFpGrowthResults(rulesFile: File): String {
        val rule = rulesFile.useLines { it.firstOrNull() }
        return fpGrowthRuleRegex.find(rule as CharSequence)!!.value.split(" ==> ")[1]
    }

    private fun <V> mineByDecisionTree(
        database: List<V>,
        sourcePredicates: List<Predicate<V>>,
        targetPredicate: Predicate<V>
    ): String {
        try {
            logger.info("Processing decision tree")
            val rulesResults = getOutpuFilePath(Miner.DECISION_TREE)

            val attributes = sourcePredicates.map { NumericAttribute(it.name()) }.toTypedArray()
            val x = (0 until database.size).map { i ->
                sourcePredicates.map { predicate ->
                    if (predicateCheck(predicate, i, database)) 1.0 else 0.0
                }.toDoubleArray()
            }.toTypedArray()
            val y = (0 until database.size).map { i ->
                if (predicateCheck(targetPredicate, i, database)) 1 else 0
            }.toIntArray()

            val rulesPath = DecionTreeMiner.mine(attributes, x, y, rulesResults)
            logger.info("Decision tree rules saved to $rulesPath")

            return rulesPath
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return ""
        }
    }

    private fun getOutpuFilePath(miner: Miner): Path {
        val timestamp = timestamp()
        val prefix = when (miner) {
            Miner.DECISION_TREE -> "tree"
            Miner.FISHBONE -> "fishbone"
            Miner.FP_GROWTH -> "fpgrowth"
        }
        val ext = when (miner) {
            Miner.DECISION_TREE -> "dot"
            Miner.FISHBONE -> "csv"
            Miner.FP_GROWTH -> "txt"
        }
        return outputFolder / ("$prefix _rules_$timestamp.$ext")
    }

    private fun timestamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss"))
}