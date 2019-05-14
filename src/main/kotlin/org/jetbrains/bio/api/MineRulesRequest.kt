package org.jetbrains.bio.api

class MineRulesRequest(
    val experiment: ExperimentType,
    val predicates: List<String>,
    val target: String? = null,
    val database: String,
    val miners: Set<Miner>
)