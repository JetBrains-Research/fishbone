package org.jetbrains.bio.experiments.rules

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.Miner
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.util.PredicatesHelper
import java.nio.file.Paths

class ChiantiDataExperiment(
    outputFolder: String = "/home/nlukashina/education/bioinf/spring/fishbone_materials/chianti_output"
) : Experiment(outputFolder) {
    override fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(i as V)

    override fun run(mineRulesRequest: MineRulesRequest): Map<Miner, String> {

        val databasePath = Paths.get(mineRulesRequest.database)
        val database = databasePath.toFile().useLines { it.map { it.toInt() }.toList() }
        val sourcePredicates = PredicatesHelper.createOverlapSamplePredicates(mineRulesRequest.sources)
        val targetPredicates = PredicatesHelper.createOverlapSamplePredicates(mineRulesRequest.targets)

        return mineRulesRequest.miners.map { miner ->
            miner to when (miner) {
                Miner.DECISION_TREE -> mineByDecisionTree(
                    database,
                    sourcePredicates,
                    targetPredicates
                )
                Miner.FISHBONE -> mineByFishbone(database, sourcePredicates, targetPredicates)
                Miner.FP_GROWTH -> mineByFPGrowth(database, sourcePredicates, targetPredicates)
            }
        }.toMap()
    }

}