package org.jetbrains.bio.predicates

import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList

/**
 * Simple overlap predicate ignoring strand
 */
class OverlapPredicate(val name: String,
                       val list: LocationsMergingList) : Predicate<Location>() {
    override fun test(item: Location) = list.intersectsBothStrands(item)

    override fun name(): String {
        return name
    }
}
