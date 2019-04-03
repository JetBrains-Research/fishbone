package org.jetbrains.bio.util.ciofani

class CiofaniCheckQuery(
        val sourcePredicates: Map<CiofaniTFsFileColumn, (String) -> Boolean>,
        val targetPredicate: Pair<CiofaniTFsFileColumn, (String) -> Boolean>
)