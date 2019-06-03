package org.jetbrains.bio.rules.decisiontree

import smile.classification.DecisionTree
import smile.data.NumericAttribute
import java.nio.file.Path

/**
 * Miner run decision tree algorithm on specified data
 */
class DecionTreeMiner {

    companion object {

        /**
         * Method constructs decision tree, writes it to file and returns path to this file
         */
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