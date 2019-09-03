package org.jetbrains.bio.fishbone.api

/**
 * Experiment type affects the way in which input data files will be processed.
 * Use GENOME for BED data files and FEATURE_SET for feature set data files.
 */
enum class ExperimentType {
    FEATURE_SET,
    GENOME
}