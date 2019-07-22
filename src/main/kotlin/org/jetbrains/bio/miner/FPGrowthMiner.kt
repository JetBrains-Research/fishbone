package org.jetbrains.bio.miner

import org.slf4j.LoggerFactory
import org.jetbrains.bio.predicate.Predicate
import weka.associations.DefaultAssociationRule
import weka.associations.FPGrowth
import weka.core.SelectedTag

/**
 * Miner run FP-growth algorithm on specified data
 * {@see: https://en.wikipedia.org/wiki/Association_rule_learning#FP-growth_algorithm}
 */
class FPGrowthMiner : WekaMiner() {
    private val logger = LoggerFactory.getLogger(FPGrowthMiner::class.java)

    override fun <V> mine(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
            params: Map<String, Any>
    ): List<List<FishboneMiner.Node<V>>> {
        try {
            if (targets.isEmpty()) {
                return emptyList()
            }

            val instancesByTarget = createInstances(database, predicates, targets, predicateCheck)
            val predicatesByName = (predicates + targets).associateBy { it.name() }

            return instancesByTarget.map { (target, instances) ->
                val fpGrowth = FPGrowth()
                // set conviction as rule's ordering metric
                logger.warn("Conviction is oly allowed to order rules in FP-Growth algorithm")
                fpGrowth.metricType = SelectedTag(3, DefaultAssociationRule.TAGS_SELECTION)
                fpGrowth.buildAssociations(instances)

                buildRuleNodes(fpGrowth, predicatesByName, target, database)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return emptyList()
        }
    }

    /**
     * Represent FP-Growth results in a form of Fishbone graph.
     * NOTE: cannot be abstract function of WekaMiner class because of different hierarchy of Weka algorithms
     */
    private fun <V> buildRuleNodes(
            fpGrowth: FPGrowth, predicatesByName: Map<String, Predicate<V>>, target: Predicate<V>, database: List<V>
    ): List<FishboneMiner.Node<V>> {
        return fpGrowth.associationRules.rules.map { rule ->
            val conditions = rule.consequence.map {
                val predicateName = it.attribute.name()
                if (!predicatesByName.containsKey(predicateName)) {
                    throw IllegalArgumentException("Invalid key: $predicateName")
                }
                predicatesByName.getValue(predicateName)
            }
            listOfNodes(conditions, target, database)
        }.flatten()
    }

}