package org.jetbrains.bio.experiments.rules

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.dataset.CellId
import org.jetbrains.bio.dataset.DataConfig
import org.jetbrains.bio.dataset.DataType
import org.jetbrains.bio.genome.GenomeQuery
import java.awt.Color

interface Experiment {

    fun run(mineRulesRequest: MineRulesRequest): String

    fun generatePalette(): (String) -> Color = { name ->
        val modification = modification(name)
        if (modification != null) {
            trackColor(modification, null)
        } else {
            Color.WHITE
        }
    }

    private fun modification(predicate: String, configuration: DataConfig? = null): String? {
        val m = "H3K\\d{1,2}(?:ac|me\\d)".toRegex(RegexOption.IGNORE_CASE).find(predicate) ?: return null
        if (configuration != null && m.value !in configuration.dataTypes()) {
            return null
        }
        return m.value
    }

    /**
     * Default colors by dataTypes
     */
    private fun trackColor(dataTypeId: String, cell: CellId? = null): Color {
        val color = when (dataTypeId.toLowerCase()) {
            "H3K27ac".toLowerCase() -> Color(255, 0, 0)
            "H3K27me3".toLowerCase() -> Color(153, 0, 255)
            "H3K4me1".toLowerCase() -> Color(255, 153, 0)
            "H3K4me3".toLowerCase() -> Color(51, 204, 51)
            "H3K36me3".toLowerCase() -> Color(0, 0, 204)
            "H3K9me3".toLowerCase() -> Color(255, 0, 255)
            DataType.METHYLATION.name.toLowerCase() -> Color.green
            DataType.TRANSCRIPTION.name.toLowerCase() -> Color.red
            else -> Color(0, 0, 128) /* IGV_DEFAULT_COLOR  */
        }
        return if (cell?.name == "OD") color.darker() else color
    }
}