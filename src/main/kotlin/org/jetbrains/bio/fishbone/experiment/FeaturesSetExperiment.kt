package org.jetbrains.bio.fishbone.experiment

import org.jetbrains.bio.fishbone.api.MineRulesRequest
import org.jetbrains.bio.fishbone.api.MiningAlgorithm
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.PredicatesConstructor
import java.nio.file.Paths

/**
 * Experiment for feature set dataset
 */
class FeaturesSetExperiment(outputFolder: String) : Experiment("$outputFolder/feature_set_exp_output") {
    override fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(i as V)

    override fun run(mineRulesRequest: MineRulesRequest): Map<MiningAlgorithm, String> {

        val databasePath = Paths.get(mineRulesRequest.database)
        val database = databasePath.toFile().useLines { outer -> outer.map { it.toInt() }.toList() }
        val predicates = PredicatesConstructor.createOverlapSamplePredicates(mineRulesRequest.predicates)
        val targets = PredicatesConstructor.createOverlapSamplePredicates(mineRulesRequest.targets)

        return mine(mineRulesRequest, database, predicates, targets)
    }

}