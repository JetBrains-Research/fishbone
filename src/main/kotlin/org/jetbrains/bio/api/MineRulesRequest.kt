package org.jetbrains.bio.api

class MineRulesRequest(
        val experiment: ExperimentType,
        val predicates: List<String>,
        val targets: List<String> = emptyList(),
        val database: String,
        val miners: Set<Miner>,
        val criterion: String = "conviction",
        val significanceLevel: Double? = null,
        val runName: String? = null
)