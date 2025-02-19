package org.jetbrains.bio.fishbone.predicate

import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList

class PredicatesConstructor {
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
                val samples = mutableListOf<Int>()
                val notSamples = mutableListOf<Int>()
                File(filename).useLines {
                    it.forEach { line ->
                        if (line.startsWith("not: ")) {
                            val id = line.trim().replace("not: ", "").toInt()
                            notSamples.add(id)
                        } else {
                            val id = line.trim().toInt()
                            samples.add(id)
                        }
                    }
                }
                val fileSeparator = FileSystems.getDefault().separator
                val name = filename.split(fileSeparator).last().split(".")[0]
                OverlapSamplePredicate(name, samples, notSamples)
            }
        }
    }
}