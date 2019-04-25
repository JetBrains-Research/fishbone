package org.jetbrains.bio.util

import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.predicates.OverlapPredicate
import org.jetbrains.bio.predicates.Predicate
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList

class BedFileHelper {
    companion object {

        /**
         * Creates overlap predicates from bed files
         */
        fun createPredicates(genomeQuery: GenomeQuery, filesUrls: List<String>):
                CopyOnWriteArrayList<Predicate<Location>> {
            val result = CopyOnWriteArrayList<Predicate<Location>>()

            result.addAll(filesUrls.map { url ->
                val wcPeaks = LocationsMergingList.load(genomeQuery, Paths.get(url))
                OverlapPredicate(url.split("/").last().split(".")[0], wcPeaks)
            })

            return result
        }
    }
}