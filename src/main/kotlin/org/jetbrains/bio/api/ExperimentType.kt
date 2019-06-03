package org.jetbrains.bio.api

/**
 * Experiment type affects the way in which data files will be processed.
 * Use CIOFANI for data files from <a href="https://www.cell.com/abstract/S0092-8674(12)01123-3">Ciofani's article</a>
 * and CHIANTI for data files from InChianti dataset. COMMON type is not supported yet.
 */
enum class ExperimentType {
    COMMON,
    CHIANTI,
    CIOFANI
}