package org.jetbrains.bio.rules

import org.jetbrains.bio.predicates.AndPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.util.ExperimentHelper
import weka.core.Attribute
import weka.core.Instances
import weka.core.SparseInstance

abstract class WekaMiner: Miner {

    protected fun <V> createInstances(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean
    ): Map<Predicate<V>, Instances> {
        return targets.map { target ->
            val instances = createInstancesWithAttributesFromPredicates(target, predicates, database.size)
            addInstances(database, predicates + target, instances, predicateCheck)
            target to instances
        }.toMap()
    }

    private fun <V> createInstancesWithAttributesFromPredicates(
            target: Predicate<V>,
            predicates: List<Predicate<V>>,
            capacity: Int,
            name: String = ExperimentHelper.timestamp()
    ): Instances {
        val classAttribute = Attribute(target.name(), listOf("1.0", "0.0"))
        val attributes = predicates.map { predicate -> Attribute(predicate.name()) } + classAttribute
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
            val attributesValues = predicates.map { predicate ->
                if (predicateCheck(predicate, i, database)) 1.0 else 0.0
            }.toDoubleArray()
            instances.add(SparseInstance(1.0, attributesValues)) //TODO: type of instance?
        }
    }

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