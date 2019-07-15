package org.jetbrains.bio.rules

import org.jetbrains.bio.predicates.Predicate

// TODO: add Fishbone miner to successors
interface Miner {
    fun <V> mine(
            database: List<V>,
            predicates: List<Predicate<V>>,
            targets: List<Predicate<V>>,
            predicateCheck: (Predicate<V>, Int, List<V>) -> Boolean,
            params: Map<String, Any>
    ): List<List<FishboneMiner.Node<V>>>
}