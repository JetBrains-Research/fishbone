package org.jetbrains.bio.util.chianti.model

enum class RedundantFeature(val label: String) {
    Folcg("FOLICG"),
    Vib12g("VIB12G"),
    Vitb6g("VITB6G"),
    ohde25("OHDE"),
    Igfbp3c("IGFBP3C"),
    Cortis("CORTIS"),
    Deas("DHEAS"),
    Testo("TESTO"),
    Freets("FREETS"),
    Bioats("BIOATS"),
    Estidio("ESTDIO"),
    C140b("C14_0B"),
    C141b("C14_1B"),
    C160b("C16_0B"),
    C161b("C16_1B"),
    C180b("C18_0B"),
    C189b("C18_9B"),
    C187b("C18_7B"),
    C186b("C18_6B"),
    C183b("C18_3B"),
    C200b("C20_0B"),
    C201b("C20_1B"),
    C202b("C20_2B"),
    C203b("C20_3B"),
    C204b("C20_4B"),
    C205b("C20_5B"),
    C220b("C22_0B"),
    C221b("C22_1B"),
    C226b("C22_6B"),
    C240b("C24_0B"),
    C241b("C24_1B");

    companion object {
        fun labels(): Set<String> = values().map { it.label }.toSet()
    }
}