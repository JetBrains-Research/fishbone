package org.jetbrains.bio.api

class ExperimentSettings(
        val topRules: Int = 10,
        val exploratoryFraction: Double = 0.5,
        val nSampling: Int = 200,
        val samplingStrategy: SamplingStrategy = SamplingStrategy.NONE,
        val alphaHoldout: Double = 0.2,
        val alphaFull: Double = 0.2
)