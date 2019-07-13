package org.jetbrains.bio.predicates

import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import java.util.*

/**
 * Simple overlap predicate ignoring strand
 */
class OverlapPredicate(val name: String,
                       val list: LocationsMergingList) : Predicate<Location>() {
    override fun test(item: Location) = list.intersectsBothStrands(item)

    override fun name(): String {
        return name
    }

    @Volatile
    private var cache: Pair<List<Location>, BitSet>? = null

    @Synchronized
    override fun testUncached(items: List<Location>): BitSet {
        // NOTE: We use reference equality check instead of Lists equality ===
        // because it can be slow on large databases.
        val lastCache = cache
        if (lastCache?.first === items) {
            return lastCache.second
        }
        val result = super.testUncached(items)
        cache = items to result
        return result
    }
}
