package org.jetbrains.bio.experiment

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.predicate.Predicate
import org.jetbrains.bio.predicate.PredicatesConstructor
import java.nio.file.Paths

/**
 * Experiment for InChianti dataset {@see: http://inchiantistudy.net/wp/inchianti-dataset/}
 */
class ChiantiDataExperiment(outputFolder: String) : Experiment("$outputFolder/chianti_based_exp_output") {
    override fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(i as V)

    override fun run(mineRulesRequest: MineRulesRequest): Map<MiningAlgorithm, String> {

        val databasePath = Paths.get(mineRulesRequest.database)
        val database = databasePath.toFile().useLines { outer -> outer.map { it.toInt() }.toList() }
        val predicates = PredicatesConstructor.createOverlapSamplePredicates(mineRulesRequest.predicates)
        val targets = PredicatesConstructor.createOverlapSamplePredicates(mineRulesRequest.targets)

        return mine(mineRulesRequest, database, predicates, targets)
    }

}