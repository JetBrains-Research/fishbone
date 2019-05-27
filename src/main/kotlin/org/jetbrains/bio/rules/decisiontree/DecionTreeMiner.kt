package org.jetbrains.bio.rules.decisiontree

import smile.classification.DecisionTree
import smile.data.NumericAttribute
import java.nio.file.Path

class DecionTreeMiner {

    companion object {
        fun mine(
            attributes: Array<NumericAttribute>,
            x: Array<DoubleArray>,
            y: IntArray,
            outputFilePath: Path,
            maxNodes: Int = 8
        ): String {
            val tree = DecisionTree(attributes, x, y, maxNodes)
            val outputFile = outputFilePath.toFile()
            outputFile.writeText(tree.dot())
            return outputFile.absolutePath
        }
    }
}