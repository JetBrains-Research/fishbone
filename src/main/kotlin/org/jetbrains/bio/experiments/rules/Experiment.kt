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
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

abstract class Experiment(private val outputFolder: String) {

    class PredicateInfo(val id: Int, val name: String, val satisfactionOnIds: BitSet)

    abstract fun run(mineRulesRequest: MineRulesRequest): Map<Miner, String>
    abstract fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean

    private val logger = Logger.getLogger(Experiment::class.java)

    fun <V> mineByDecisionTree(
        database: List<V>,
        sourcePredicates: List<Predicate<V>>,
        targetPredicates: List<Predicate<V>>
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
            // TODO: use all targets
            val y = (0 until database.size).map { i ->
                if (predicateCheck(targetPredicates[0], i, database)) 1 else 0
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

    fun <V> mineByFPGrowth(
        database: List<V>,
        sourcePredicates: List<Predicate<V>>,
        targetPredicates: List<Predicate<V>>
    ): String {
        try {
            logger.info("Processing fp-growth")
            val rulesResults = getOutpuFilePath(Miner.FP_GROWTH)

            val sourcePredicatesInfo = getPredicatesInfoOverDatabase(sourcePredicates, database, "1")
            val sourceIdsToNames = sourcePredicatesInfo.map { it.id to it.name }.toMap()
            val targetPredicatesInfo = getPredicatesInfoOverDatabase(targetPredicates, database, "2")
            val targetIdsToNames = targetPredicatesInfo.map { it.id to it.name }.toMap()

            val items = database.withIndex().map { (idx, _) ->
                val satisfiedSources = getSatisfiedPredicateIds(sourcePredicatesInfo, idx)
                val satisfiedTargets = getSatisfiedPredicateIds(targetPredicatesInfo, idx)
                (satisfiedSources + satisfiedTargets).toIntArray()
            }.toTypedArray()

            val rulesPath = FPGrowthMiner.mine(
                items,
                sourcePredicatesInfo,
                sourceIdsToNames,
                targetPredicatesInfo,
                targetIdsToNames,
                rulesResults
            )
            logger.info("FPGrowth rules saved to $rulesPath")

            return rulesPath
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return ""
        }
    }

    private fun <V> getPredicatesInfoOverDatabase(predicates: List<Predicate<V>>, database: List<V>, suffix: String)
            : List<PredicateInfo> {
        return predicates.withIndex().map { (idx, predicate) ->
            val id = (idx.toString() + suffix).toInt()
            PredicateInfo(id, predicate.name(), predicate.test(database))
        }
    }

    private fun getSatisfiedPredicateIds(predicates: List<PredicateInfo>, itemIdx: Int) =
        predicates.filter { it.satisfactionOnIds[itemIdx] }.map { it.id }

    fun <V> mineByFishbone(
        database: List<V>,
        sourcePredicates: List<Predicate<V>>,
        targetPredicates: List<Predicate<V>>
    ): String {
        try {
            logger.info("Processing fishbone")
            val rulesResults = getOutpuFilePath(Miner.FISHBONE)
            val rulesLogger = RulesLogger(rulesResults)

            RulesMiner.mine(
                "All => All",
                database,
                targetPredicates.map { sourcePredicates to it },
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

    fun getOutpuFilePath(miner: Miner): Path {
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