package org.jetbrains.bio.api

class MineRulesRequest(
    val experiment: ExperimentType,
    val predicates: List<String>,
    val target: List<String> = emptyList(),
    val database: String,
    val miners: Set<Miner>,
    val criterion: String = "conviction",
    val checkSignificance: Boolean = true,
    val runName: String? = null
)