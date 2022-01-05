package org.jetbrains.bio.fishbone.rule.visualize.upset

data class UpsetRecord(val id: List<Int>, val n: Int) {
    override fun toString(): String {
        return "${id.joinToString(",") { it.toString() }}:$n"
    }
}