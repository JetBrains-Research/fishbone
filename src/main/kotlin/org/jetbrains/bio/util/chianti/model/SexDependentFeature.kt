package org.jetbrains.bio.util.chianti.model

enum class SexDependentFeature(val label: String) {
    Urico("URICO"),
    Adipon("ADIPON"),
    Palk("PALK"),
    Tigf1("TIGF1"),
    Frigf1("FRIGF1"),
    Freets("FREETS"),
    Bioats("BIOATS"),
    Cntme("CNTME"),
    Ggt("GGT"),
    Got("GOT"),
    Gpt("GPT"),
    Gr("GR"),
    LpA("LP_A"),
    Piinp("PIIINP"),
    Shbg("SHBG"),
    Tenmol("TENMOL"),
    Tf("TF"),
    Tsshbg("TSSHBG"),
    Uca("UCA"),
    Ves("VES"),
    Vgm("VGM"),
    Xbioatsm("XBIOATSM"),
    Xfreetsm("XFREETSM"),
    Ybioatsm("YBIOATSM"),
    Zbioatsm("ZBIOATSM"),
    Zfreetsm("ZFREETSM"),
    Crea("CREA"),
    Cnme("CNCME"),
    Colhdl("COLHDL"),
    Ferro("FERRO"),
    Fiin("FTIN"),
    Hb("HB"),
    Ucreat("UCREAT"),
    Folicm("FOLICM"),
    Ratio_C18_9_C18_6("Ratio_C18_9_C18_6");

    companion object {
        fun labels(): Set<String> = values().map { it.label }.toSet()
    }
}