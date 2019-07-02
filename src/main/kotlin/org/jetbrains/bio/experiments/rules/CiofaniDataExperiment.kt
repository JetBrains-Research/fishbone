package org.jetbrains.bio.experiments.rules

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.Miner
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.util.PredicatesHelper
import java.nio.file.Paths

class CiofaniDataExperiment(outputFolder: String) : Experiment("$outputFolder/ciofani_output") {
    override fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(db[i])

    private val genomeQuery = GenomeQuery("mm10")

    override fun run(mineRulesRequest: MineRulesRequest): Map<Miner, String> {
        val databasePath = Paths.get(mineRulesRequest.database)
        val database = LocationsMergingList.load(genomeQuery, databasePath).toList()
        val predicates = PredicatesHelper.createBedPredicates(genomeQuery, mineRulesRequest.predicates)
        val targets = if (mineRulesRequest.target.isNotEmpty()) {
            PredicatesHelper.createBedPredicates(genomeQuery, mineRulesRequest.target)
        } else {
            null
        }

        return mine(mineRulesRequest, database, predicates, targets)
    }

}