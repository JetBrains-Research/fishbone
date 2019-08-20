package org.jetbrains.bio.api

import org.jetbrains.bio.rules.sampling.SamplingStrategy

class ExperimentSettings(
        val topRules: Int = 10,
        val exploratoryFraction: Double = 0.5,
        val nSampling: Int = 120,
        val samplingStrategy: SamplingStrategy = SamplingStrategy.NONE,
        val alphaHoldout: Double = 0.2,
        val alphaFull: Double = 0.2
)