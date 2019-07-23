package org.jetbrains.bio.rules.validation

import junit.framework.TestCase
import org.jetbrains.bio.predicates.ProbePredicate
import org.jetbrains.bio.rules.FishboneMiner
import org.jetbrains.bio.rules.Rule
import org.junit.Test

// TODO: investigate Fisher's test severity
class FisherExactCheckTest : TestCase() {

    private val statCheck = FisherExactCheck()

    @Test
    fun testRandom() {
        val database = (0.until(100)).toList()
        val probes = (0.until(10)).map { ProbePredicate("probe_$it", database) }
        val target = ProbePredicate("target", database)
        val result = FishboneMiner.mine(
                probes,
                target,
                database,
                maxComplexity = 2,
                function = Rule<Int>::conviction
        ).first()
        assertFalse(statCheck.testRule(result.rule, database) < 0.05)
    }

}