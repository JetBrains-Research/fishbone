package org.jetbrains.bio.fishbone.miner

import org.jetbrains.bio.fishbone.predicate.AndPredicate
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.rule.Rule
import weka.core.Attribute
import weka.core.Instances
import weka.core.SparseInstance

abstract class WekaMiner : Miner {

    /**
     * Create Weka Instances for each target.
     *
     * @param database database
     * @param predicates list of predicates over database
     * @param targets list of targets over database
     * @param predicateCheck function to test predicate against element at specified position in database
     *
     * @return map of Instances per target
     */
    protected fun <V> createInstances(
        database: List<V>,
        predicates: List<Predicate<V>>,
        targets: List<Predicate<V>>,
        predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean
    ): Map<Predicate<V>, Instances> {
        return targets.map { target ->
            val instances = createInstancesWithAttributesFromPredicates(
                target.name(), predicates.map { it.name() }, database.size
            )
            addInstances(database, predicates + target, instances, predicateCheck)
            target to instances
        }.toMap()
    }

    private fun createInstancesWithAttributesFromPredicates(
        targetName: String, predicateNames: List<String>, capacity: Int, name: String = Miner.timestamp()
    ): Instances {
        // Create attributes for target and predicates
        val classAttribute = Attribute(targetName, listOf("1.0", "0.0"))
        val attributes = predicateNames.map { p -> Attribute(p, listOf("1.0", "0.0")) } + classAttribute

        // Create Instances with class index at target
        val instances = Instances(name, ArrayList(attributes), capacity)
        instances.setClassIndex(instances.numAttributes() - 1)

        return instances
    }

    private fun <V> addInstances(
        database: List<V>,
        predicates: List<Predicate<V>>,
        instances: Instances,
        predicateCheck: (p: Predicate<V>, i: Int, db: List<V>) -> Boolean
    ) {
        (0 until database.size).map { i ->
            // Attribute value is 1.0 if predicate is TRUE on a sample and 0.0 otherwise
            val attributesValues = predicates.map { predicate ->
                if (predicateCheck(predicate, i, database)) 1.0 else 0.0
            }.toDoubleArray()
            instances.add(SparseInstance(1.0, attributesValues))
        }
    }

    /**
     * Combine rule: merge conditions and target to the list of nodes
     */
    protected fun <V> listOfNodes(
        conditions: List<Predicate<V>>, target: Predicate<V>, database: List<V>
    ): List<FishboneMiner.Node<V>> {
        return if (conditions.isEmpty()) {
            emptyList()
        } else {
            val firstPredicate = conditions.first()
            val firstNode = FishboneMiner.Node(Rule(firstPredicate, target, database), firstPredicate, null)
            conditions.drop(1).fold(
                listOf(firstNode), { nodes, p -> nodes + buildNode(nodes, p, target, database) }
            )
        }
    }

    private fun <V> buildNode(
        nodes: List<FishboneMiner.Node<V>>, p: Predicate<V>, target: Predicate<V>, database: List<V>
    ): FishboneMiner.Node<V> {
        val parent = nodes.last()
        val newPredicate = AndPredicate(listOf(parent.rule.conditionPredicate, p))
        return FishboneMiner.Node(Rule(newPredicate, target, database), p, parent)
    }
}