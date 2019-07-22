package org.jetbrains.bio.util.chianti.variable

enum class CombinedFeature(
        val title: String,
        val labels: Collection<String>,
        val combine: (Map<String, Double>) -> Double,
        val preserveSources: Boolean
) {
    VIT_E(
            "VIT_E",
            listOf("GAMTOC", "ALFTOC"),
            { params: Map<String, Double> ->
                params.values.sum()
            },
            false
    ),
    Ratio_C18_9_C18_6(
            "Ratio_C18_9_C18_6",
            listOf("C18_9D", "C18_6D"),
            { params: Map<String, Double> ->
                params.getValue("C18_9D") / params.getValue("C18_6D")
            },
            true
    );

    companion object {
        fun getByLabel(label: String): CombinedFeature? {
            return values().find { it.labels.contains(label) }
        }

        fun getByTitle(label: String): CombinedFeature? {
            return values().find { it.title == label }
        }
    }
}