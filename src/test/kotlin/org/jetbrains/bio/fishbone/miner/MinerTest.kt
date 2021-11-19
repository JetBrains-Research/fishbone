package org.jetbrains.bio.fishbone.miner

import org.jetbrains.bio.fishbone.api.MiningAlgorithm
import org.jetbrains.bio.fishbone.predicate.OverlapSamplePredicate
import org.jetbrains.bio.fishbone.predicate.TruePredicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinerTest {

    @Test
    fun testUpdateStatistics() {
        val database = listOf(1, 2, 3, 4, 5)
        val a = OverlapSamplePredicate("A", listOf(1, 2, 3, 5), listOf(4))
        val target = OverlapSamplePredicate("T", listOf(1, 2, 3, 5), listOf(4))

        val rule = Rule(a, target, database)
        val node = FishboneMiner.Node(rule, a, null)
        assertEquals(5, rule.database)
        assertEquals(4, rule.condition)
        assertEquals(4, rule.target)
        assertEquals(4, rule.intersection)

        val newDatabase = listOf(1, 2, 4, 5)

        val updated = Miner.updateRulesStatistics(
            listOf(MiningAlgorithm.FISHBONE to listOf(node)),
            target,
            newDatabase
        )[0].second[0].rule
        assertEquals(4, updated.database)
        assertEquals(3, updated.condition)
        assertEquals(3, updated.target)
        assertEquals(3, updated.intersection)

        assertTrue(rule.condition > updated.conviction)
        assertTrue(rule.loe > updated.loe)
    }

    @Test
    fun testUpdateStatisticsAux() {
        val database = listOf(1, 2, 3, 4, 5)
        val a = OverlapSamplePredicate("A", listOf(1, 2, 3), listOf(4))
        val b = OverlapSamplePredicate("A", listOf(5), listOf(4))
        val target = OverlapSamplePredicate("T", listOf(1, 2, 3, 5), listOf(4))

        val parentRule = Rule(a, target, database)
        val parentNode = FishboneMiner.Node(parentRule, a, null)
        val rule = Rule(a.or(b), target, database)
        val node = FishboneMiner.Node(rule, b, parentNode)

        val fakeRule = Rule(TruePredicate(), target, database)
        val fakeNode = FishboneMiner.Node(fakeRule, TruePredicate(), null)

        val newDatabase = listOf(1, 2, 4, 5)

        val updated = Miner.updateRulesStatistics(
            listOf(MiningAlgorithm.FISHBONE to listOf(node, fakeNode)),
            target,
            newDatabase
        )

        assertNotNull(updated[0].second[1].visualizeInfo)
    }
}