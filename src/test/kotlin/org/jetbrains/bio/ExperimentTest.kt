package org.jetbrains.bio

import org.jetbrains.bio.api.SamplingStrategy
import org.jetbrains.bio.experiment.ChiantiDataExperiment
import org.jetbrains.bio.predicate.OverlapSamplePredicate
import org.junit.AfterClass
import org.junit.Test
import kotlin.test.assertEquals

class ExperimentTest {

    @Test
    fun testSample() {
        val database = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val samples = listOf(1, 2, 3, 4)
        val target = OverlapSamplePredicate("target", samples, database.subtract(samples).toList())

        val downsampled = experiment.sample(database, target, SamplingStrategy.DOWNSAMPLING)
        assertEquals(4, downsampled.filter { it in samples }.count())
        assertEquals(4, downsampled.filter { it !in samples }.count())

        val upsampled = experiment.sample(database, target, SamplingStrategy.UPSAMPLING)
        assertEquals(6, upsampled.filter { it in samples }.count())
        assertEquals(6, upsampled.filter { it !in samples }.count())
    }

    companion object {
        private val tempDir = createTempDir("temp-${System.currentTimeMillis()}")
        val experiment = ChiantiDataExperiment(tempDir.absolutePath)

        @AfterClass
        fun init() {
            tempDir.deleteRecursively()
        }
    }
}