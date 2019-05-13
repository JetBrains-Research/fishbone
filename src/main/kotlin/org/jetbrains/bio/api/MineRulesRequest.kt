package org.jetbrains.bio.api

class MineRulesRequest(
    val experiment: ExperimentType,
    val sources: List<String>,
    val targets: List<String>,
    val database: String,
    val miners: Set<Miner> //TODO: sets
)