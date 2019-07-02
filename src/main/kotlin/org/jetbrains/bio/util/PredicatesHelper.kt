package org.jetbrains.bio.util

import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.predicates.OverlapSamplePredicate
import org.jetbrains.bio.predicates.OverlapPredicate
import org.jetbrains.bio.predicates.Predicate
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList

class PredicatesHelper {
    companion object {

        /**
         * Creates overlap predicates from bed files
         */
        fun createBedPredicates(genomeQuery: GenomeQuery, filesUrls: List<String>): List<Predicate<Location>> {
            val result = CopyOnWriteArrayList<Predicate<Location>>()

            result.addAll(filesUrls.map { url ->
                val wcPeaks = LocationsMergingList.load(genomeQuery, Paths.get(url))
                OverlapPredicate(url.split("/").last().split(".")[0], wcPeaks)
            })

            return result
        }

        /**
         * Creates overlap sample predicates from txt files
         */
        fun createOverlapSamplePredicates(filesUrls: List<String>): List<OverlapSamplePredicate> {
            return filesUrls.map { filename ->
                val samples = File(filename).useLines { it.map { it.toInt() }.toList() }
                val name = filename.split("/").last().split(".")[0]
                OverlapSamplePredicate(name, samples)
            }
        }
    }
}