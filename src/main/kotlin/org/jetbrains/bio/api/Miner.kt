package org.jetbrains.bio.api

/**
 * Mining algorithms
 */
enum class Miner(val label: String, val ext: String, val isFishboneSupported: Boolean) {
    FISHBONE("fishbone", "csv", true),
    RIPPER("ripper", "csv", true),
    DECISION_TREE("tree", "dot", false),
    FP_GROWTH("fpgrowth", "txt", false);

    companion object {
        fun byLable(name: String): Miner? = values().find { it.label == name }
    }

    override fun toString(): String {
        return label
    }
}