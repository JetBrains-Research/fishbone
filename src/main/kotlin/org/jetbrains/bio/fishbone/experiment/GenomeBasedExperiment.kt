package org.jetbrains.bio.fishbone.experiment

import org.jetbrains.bio.fishbone.api.MineRulesRequest
import org.jetbrains.bio.fishbone.api.MiningAlgorithm
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.PredicatesConstructor
import org.jetbrains.bio.genome.Genome
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.containers.LocationsMergingList
import java.nio.file.Paths

/**
 * Experiment for genome based data (BED files).
 */
class GenomeBasedExperiment(outputFolder: String) : FarmExperiment("$outputFolder/genome_based_exp_output") {
    override fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(db[i])

    override fun run(mineRulesRequest: MineRulesRequest): Map<MiningAlgorithm, String> {
        val genomeQuery = GenomeQuery(Genome[mineRulesRequest.genome])
        val databasePath = Paths.get(mineRulesRequest.database)
        val database = LocationsMergingList.load(genomeQuery, databasePath).toList()
        val predicates = PredicatesConstructor.createBedPredicates(genomeQuery, mineRulesRequest.predicates)
        val targets = PredicatesConstructor.createBedPredicates(genomeQuery, mineRulesRequest.targets)

        return mine(mineRulesRequest, database, predicates, targets)
    }

}