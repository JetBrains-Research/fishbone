package org.jetbrains.bio.util.ciofani

open class CiofaniCheckQuery(
        val sourcePredicates: Map<CiofaniTFsFileColumn, (List<String>) -> Boolean>, // TODO: vararg?
        val targetPredicate: Pair<CiofaniTFsFileColumn, (List<String>) -> Boolean>
)