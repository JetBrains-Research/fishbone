package org.jetbrains.bio.util.ciofani.query

import org.jetbrains.bio.util.ciofani.CiofaniCheckQuery
import org.jetbrains.bio.util.ciofani.CiofaniTFsFileColumn

class AllTfsToRorcQuery : CiofaniCheckQuery(
    mapOf(
        CiofaniTFsFileColumn.TFS to fun(params): Boolean {
            return params[0] in listOf("Batf", "IRF4", "Maf", "Stat3")
        }
    ),
    Pair(
        CiofaniTFsFileColumn.TFS, fun(params): Boolean { return params[0] == "RORC" }
    )
)