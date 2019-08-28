package org.jetbrains.bio

import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.api.SamplingStrategy
import org.jetbrains.bio.experiment.ChiantiDataExperiment
import org.jetbrains.bio.miner.FishboneMiner
import org.jetbrains.bio.predicate.OverlapSamplePredicate
import org.jetbrains.bio.predicate.ProbePredicate
import org.jetbrains.bio.rule.Rule
import org.junit.AfterClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun testSplitDataset() {
        val database = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val samples = listOf(1, 2, 3, 4)
        val target = OverlapSamplePredicate("target", samples, database.subtract(samples).toList())

        val (exploratory, holdout) = experiment.splitDataset(database, target, SamplingStrategy.NONE, 0.1)
        assertEquals(1, exploratory.size)
        assertEquals(9, holdout.size)
    }

    @Test
    fun testGetProductiveRules() {
        val database = (0.until(100)).toList()
        val probes = (0.until(10)).map { ProbePredicate("probe_$it", database) }
        val target = ProbePredicate("target", database)
        val rules = FishboneMiner.mine(
                probes,
                target,
                database,
                maxComplexity = 2,
                function = Rule<Int>::conviction
        )

        assertEquals(rules.size, experiment.getProductiveRules(MiningAlgorithm.FISHBONE, rules, 1.0, database, false).size)
        assertTrue(rules.size > experiment.getProductiveRules(MiningAlgorithm.FISHBONE, rules, 0.05, database, false).size)
    }

    companion object {
        private val tempDir = createTempDir("temp-${System.currentTimeMillis()}")
        val experiment = ChiantiDataExperiment(tempDir.absolutePath)

        @AfterClass
        fun cleanup() {
            tempDir.deleteRecursively()
        }
    }
}