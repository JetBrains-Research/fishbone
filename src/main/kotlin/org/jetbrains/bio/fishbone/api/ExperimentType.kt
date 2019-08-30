package org.jetbrains.bio.fishbone.api

/**
 * Experiment type affects the way in which input data files will be processed.
 * Use GENOME for BED data files.
 * and CHIANTI for data files from InChianti dataset
 */
enum class ExperimentType {
    CHIANTI,
    GENOME
}