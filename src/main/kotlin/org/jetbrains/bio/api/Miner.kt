package org.jetbrains.bio.api

/**
 * Mining algorithms
 */
enum class Miner(val label: String) {
    DECISION_TREE("tree"),
    FISHBONE("fishbone"),
    FP_GROWTH("fp-growth");

    companion object {
        fun byLable(name: String): Miner? = values().find { it.label == name }
    }
}