package org.jetbrains.bio.util.ciofani

enum class CiofaniTFsFileColumn(val columnName: String, val isValuePredicate: Boolean) {
    CHR("chr", false),
    TFS("expt", true),
    TFS_ALPHABETICALLY("expt.alphanum.sorted", true),
    START("start", false),
    END("end", false),
    S("s", false),
    PVAL("pval", true),
    PVAL_MEAN("pval.mean", false)
    // TODO: add other columns when necessary
}