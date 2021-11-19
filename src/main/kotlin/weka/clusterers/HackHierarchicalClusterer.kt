package weka.clusterers


/**
 * This class is used to get access to protected field of [HierarchicalClusterer]
 */
class HackHierarchicalClusterer : HierarchicalClusterer() {

    /**
     * Returns dendrogram encoding suitable for D3JS visualization.
     */
    fun getRootData(names: List<String>): Map<String, *> {
        /**
         * Inner function gives access to [HierarchicalClusterer.Node]
         */
        fun traverse(node: Node, top: Boolean = false): Map<String, Any> {
            return mapOf(
                (if (top) "totalLength" else "length") to node.m_fHeight,
                "children" to listOf(
                    if (node.m_left == null) {
                        mapOf(
                            "length" to node.m_fLeftLength,
                            "key" to names[node.m_iLeftInstance]
                        )
                    } else {
                        traverse(node.m_left)
                    },
                    if (node.m_right == null) {
                        mapOf(
                            "length" to node.m_fRightLength,
                            "key" to names[node.m_iRightInstance]
                        )
                    } else {
                        traverse(node.m_right)
                    }
                )
            )
        }

        return align(traverse(m_clusters[0], top = true)).first
    }

    /**
     * This function is used to align resulting dendrogram lengths so that
     * summary length from each leaf to root is constant.
     * Required for correct D3 JS cluster visualization.
     */
    private fun align(dendrogram: Map<String, Any>): Pair<Map<String, Any>, Double> {
        if ("key" in dendrogram) {
            return dendrogram to dendrogram["length"].toString().toDouble()
        }
        @Suppress("UNCHECKED_CAST")
        val childrenAndTotals = (dendrogram["children"] as List<Map<String, Any>>).map { align(it) }
        val children = childrenAndTotals.map { it.first }
        val childrenTotals = childrenAndTotals.map { it.second }
        val maxChildrenTotal = childrenTotals.maxOrNull()!!
        if (childrenTotals.distinct().size == 1) {
            return (dendrogram + mapOf(
                "children" to children,
                if ("totalLength" in dendrogram)
                    "totalLength" to maxChildrenTotal + 0.1
                else
                    "length" to 0.1
            )) to maxChildrenTotal + 0.1
        }
        val alignedChildren = childrenAndTotals.map {
            it.first +
                    (if ("key" in it.first)
                        mapOf("length" to maxChildrenTotal + 0.1)
                    else
                        mapOf("length" to maxChildrenTotal - it.second + 0.1))
        }

        return mapOf(
            (if ("totalLength" in dendrogram)
                "totalLength" to maxChildrenTotal + 0.2
            else "length" to 0.1),
            "children" to alignedChildren
        ) to maxChildrenTotal + 0.2
    }

    /**
     * Simple recursive DFS traversal restoring order of clustering
     */
    fun order(): List<Int> {
        val result = arrayListOf<Int>()

        /**
         * Inner function gives access to [HierarchicalClusterer.Node]
         */
        fun traverse(node: Node) {
            if (node.m_left == null) {
                result.add(node.m_iLeftInstance)
            } else {
                traverse(node.m_left)
            }
            if (node.m_right == null) {
                result.add(node.m_iRightInstance)
            } else {
                traverse(node.m_right)
            }
        }
        traverse(m_clusters[0])
        return result
    }
}

