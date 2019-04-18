package org.jetbrains.bio.util.ciofani.query

import org.jetbrains.bio.util.ciofani.CiofaniCheckQuery
import org.jetbrains.bio.util.ciofani.CiofaniTFsFileColumn
import kotlin.math.abs

class BatfNearIrf4 : CiofaniCheckQuery(
    mapOf(
        CiofaniTFsFileColumn.TFS to
                fun(params): Boolean {
                    return params[0] in listOf("Stat3", "Maf", "RORC", "Batf", "IRF4")
                }
    ),
    Pair(
        CiofaniTFsFileColumn.S,
        fun(params): Boolean {
            return abs(params[0].toInt() - params[1].toInt()) < 50
        }
    )
)