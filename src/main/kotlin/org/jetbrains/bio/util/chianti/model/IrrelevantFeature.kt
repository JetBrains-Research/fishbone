package org.jetbrains.bio.util.chianti.model

enum class IrrelevantFeature(val label: String) {
    Code98("CODE98"), // will be used only as sample id
    Site("SITE"),
    DataNas("DATA_NAS"),
    Datel("DATEL"),
    Vuoto("VUOTO"),
    Pieno("PIENO"),
    Buste("BUSTE"),
    Inzio("INIZIO"),
    Fine("FINE"),
    Ppair("PPAIR"),
    Casctl("CASCTL"),
    USedi("U_SEDI"),
    Usedia("USEDIA"),
    Lnotes("LNOTES"),
    Lcode("LCODE"),
    UClar("U_CLAR"),
    UColo("U_COLO"),
    UNote("U_NOTE");

    companion object {
        fun labels(): Set<String> = values().map { it.label }.toSet()
    }
}