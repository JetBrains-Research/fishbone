package org.jetbrains.bio.fishbone.rule.visualize

import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.rule.Rule
import weka.clusterers.HackHierarchicalClusterer
import weka.core.Attribute
import weka.core.DenseInstance
import weka.core.EuclideanDistance
import weka.core.Instances

/**
 * Data class describing pairwise correlations cluster map
 * @param tableData data required for D3JS visualization
 * @param rootData dendrogram for clustering
 */
data class Heatmap(val tableData: List<Map<String, Any>>?, val rootData: Map<String, *>) {
    companion object {
        fun <T> of(database: List<T>, predicates: List<Predicate<T>>): Heatmap {
            // Instantiate clusterer
            val clusterer = HackHierarchicalClusterer().apply {
                options = arrayOf("-L", "COMPLETE")
                debug = false
                numClusters = 1
                distanceFunction = EuclideanDistance()
                distanceIsBranchLength = true
            }
            // Build dataset
            val attributes = ArrayList(predicates.map {
                Attribute(it.name())
            })

            val data = Instances("Correlations", attributes, predicates.size)

            // Add data for clustering
            predicates.forEach { pI ->
                data.add(DenseInstance(1.0, predicates.map { pJ ->
                    Rule(pI, pJ, database).correlation
                }.toDoubleArray()))
            }

            // Cluster network
            clusterer.buildClusterer(data)

            // Clustering histogram
            val rootData = clusterer.getRootData(predicates.map { it.name() })

            // Now we should reorder predicates according to DFS in clustering histogram
            val order = clusterer.order()

            val tableData = order.map { i ->
                val pI = predicates[i]
                mapOf(
                    "key" to pI.name(),
                    "values" to order.map { j ->
                        val pJ = predicates[j]
                        mapOf(
                            "key" to pJ.name(),
                            "value" to Rule(pI, pJ, database).correlation
                        )
                    })
            }
            return Heatmap(tableData, rootData)
        }
    }
}