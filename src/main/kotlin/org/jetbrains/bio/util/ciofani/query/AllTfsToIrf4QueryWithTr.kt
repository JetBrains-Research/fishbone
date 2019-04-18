package org.jetbrains.bio.util.ciofani.query

import org.jetbrains.bio.util.ciofani.CiofaniCheckQuery
import org.jetbrains.bio.util.ciofani.CiofaniTFsFileColumn

class AllTfsToIrf4QueryWithTr : CiofaniCheckQuery(
    mapOf(
        CiofaniTFsFileColumn.TFS to
                fun(params): Boolean {
                    return params[0] in listOf("Stat3", "Maf", "RORC", "Batf") && params[1].toDouble() >= 2000
                }
    ),
    Pair(
        CiofaniTFsFileColumn.TFS,
        fun(params): Boolean {
            return params[0] == "IRF4" && params[1].toDouble() >= 2000
        }
    )
)