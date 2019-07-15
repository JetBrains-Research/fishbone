package org.jetbrains.bio.experiments.rules

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.Miner
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.util.PredicatesHelper
import java.nio.file.Paths

class ChiantiDataExperiment(outputFolder: String) : Experiment("$outputFolder/cianti_output") {
    override fun <V> predicateCheck(p: Predicate<V>, i: Int, db: List<V>): Boolean = p.test(i as V)

    override fun run(mineRulesRequest: MineRulesRequest): Map<Miner, String> {

        val databasePath = Paths.get(mineRulesRequest.database)
        val database = databasePath.toFile().useLines { outer -> outer.map { it.toInt() }.toList() }
        val predicates = PredicatesHelper.createOverlapSamplePredicates(mineRulesRequest.predicates)
        val targets = if (mineRulesRequest.targets.isNotEmpty()) {
            PredicatesHelper.createOverlapSamplePredicates(mineRulesRequest.targets)
        } else {
            emptyList()
        }

        return mine(mineRulesRequest, database, predicates, targets)
    }

}