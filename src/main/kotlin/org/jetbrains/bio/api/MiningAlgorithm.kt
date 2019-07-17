package org.jetbrains.bio.api

/**
 * Mining algorithms
 */
enum class MiningAlgorithm(val label: String) {
    FISHBONE("fishbone"),
    RIPPER("ripper"),
    DECISION_TREE("tree"),
    FP_GROWTH("fp-growth");

    companion object {
        fun byLable(name: String): MiningAlgorithm? = values().find { it.label == name }
    }

    override fun toString(): String {
        return label
    }
}