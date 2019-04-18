package org.jetbrains.bio.util.ciofani.query

import org.jetbrains.bio.util.ciofani.CiofaniCheckQuery
import org.jetbrains.bio.util.ciofani.CiofaniTFsFileColumn

class AllTfsToIrf4Query : CiofaniCheckQuery(
    mapOf(
        CiofaniTFsFileColumn.TFS to
                fun(params): Boolean {
                    return params[0] in listOf("Stat3", "Maf", "RORC", "Batf")
                }
    ),
    Pair(
        CiofaniTFsFileColumn.TFS,
        fun(params): Boolean {
            return params[0] == "IRF4"
        }
    )
)