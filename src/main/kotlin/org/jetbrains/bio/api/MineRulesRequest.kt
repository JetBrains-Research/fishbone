package org.jetbrains.bio.api

import org.jetbrains.bio.rules.sampling.SamplingStrategy

// TODO: add sampling strategy and topRules to UI and request processing
class MineRulesRequest(
        val experiment: ExperimentType,
        val predicates: List<String>,
        val targets: List<String> = emptyList(),
        val database: String,
        val miners: Set<MiningAlgorithm>,
        val criterion: String = "conviction",
        val sampling: SamplingStrategy? = null,
        val significanceLevel: Double? = null,
        val topRules: Int? = null,
        val runName: String? = null,
        val settings: ExperimentSettings = ExperimentSettings()
)