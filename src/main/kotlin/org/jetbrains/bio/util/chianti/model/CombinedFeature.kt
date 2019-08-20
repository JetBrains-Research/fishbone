package org.jetbrains.bio.util.chianti.model

enum class CombinedFeature(
        val title: String, val labels: Collection<String>, val combine: (Map<String, Double>) -> Double, params: Map<String, Double>
) {
    VIT_E(
            "VIT_E",
            listOf("GAMTOC", "ALFTOC"),
            { params: Map<String, Double> ->
                params.values.sum()
            },
            emptyMap()
    ),
    Ratio_C18_9_C18_6(
            "Ratio_C18_9_C18_6",
            listOf("C18_9D", "C18_6D"),
            { params: Map<String, Double> ->
                params.getValue("C18_9D") / params.getValue("C18_6D")
            },
            mapOf(
                    "min_male" to 0.1455674,
                    "max_male" to 2.261903,
                    "mean_male" to 1.11923,
                    "q1_male" to 0.8990796,
                    "q3_male" to 1.3147746
            )
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