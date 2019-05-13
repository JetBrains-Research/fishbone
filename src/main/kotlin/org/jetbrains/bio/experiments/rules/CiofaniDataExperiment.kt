package org.jetbrains.bio.experiments.rules

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.Miner
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.util.PredicatesHelper
import java.nio.file.Paths
import kotlin.reflect.KFunction4

class CiofaniDataExperiment(
    outputFolder: String = "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_output"
) : Experiment(outputFolder) {
    override fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(db[i])

    private val genomeQuery = GenomeQuery("mm10")

    override fun run(mineRulesRequest: MineRulesRequest): Map<Miner, String> {
        val databasePath = Paths.get(mineRulesRequest.database)
        val database = LocationsMergingList.load(genomeQuery, databasePath).toList()
        val sourcePredicates = PredicatesHelper.createBedPredicates(genomeQuery, mineRulesRequest.sources)
        val targetPredicates = PredicatesHelper.createBedPredicates(genomeQuery, mineRulesRequest.targets)

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