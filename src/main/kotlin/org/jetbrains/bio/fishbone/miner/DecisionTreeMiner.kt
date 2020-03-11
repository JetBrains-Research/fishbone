package org.jetbrains.bio.fishbone.miner

import org.jetbrains.bio.fishbone.predicate.Predicate
import org.slf4j.LoggerFactory
import weka.classifiers.rules.PART


/**
 * Miner run PART algorithm on specified data
 * {@see: http://weka.sourceforge.net/doc.dev/weka/classifiers/rules/PART.html}
 */
class DecisionTreeMiner : WekaMiner() {
    private val logger = LoggerFactory.getLogger(DecisionTreeMiner::class.java)

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
                val part = PART()
                part.buildClassifier(instances)

                buildRuleNodes(part, predicatesByName, target, database)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            logger.error(t.message)
            return emptyList()
        }
    }

    /**
     * Represent PART results in a form of Fishbone graph.
     * NOTE: cannot be abstract function of WekaMiner class because of different hierarchy of Weka algorithms
     */
    private fun <V> buildRuleNodes(
            part: PART, predicatesByName: Map<String, Predicate<V>>, target: Predicate<V>, database: List<V>
    ): List<FishboneMiner.Node<V>> {
        val rulesDescription = part.toString().split("\n\n")

        return rulesDescription.subList(1, rulesDescription.size - 2).map { ruleDescription ->
            ruleToListOfNodes(ruleDescription, predicatesByName, target, database)
        }.flatten()
    }

    /**
     * Create a list of fishbone nodes for the rule
     */
    private fun <V> ruleToListOfNodes(
            rule: String,
            predicatesByName: Map<String, Predicate<V>>,
            target: Predicate<V>,
            database: List<V>
    ): List<FishboneMiner.Node<V>> {
        val conditions = rule.split("AND").map {
            val predicateName = it.trim().trim('\n').split(" ")[0]
            if (!predicatesByName.containsKey(predicateName)) {
                throw IllegalArgumentException("Invalid key: $predicateName")
            }
            val predicate = predicatesByName.getValue(predicateName)
            if (it.contains("<")) predicate.not() else predicate
        }

        return listOfNodes(conditions, target, database)
    }
}