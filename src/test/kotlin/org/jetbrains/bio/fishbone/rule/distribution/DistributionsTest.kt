package org.jetbrains.bio.fishbone.rule.distribution

import org.jetbrains.bio.fishbone.miner.RangePredicate
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.ProbePredicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.jetbrains.bio.fishbone.rule.distribution.Distribution.Companion.kullbackLeibler
import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertTrue

class DistributionsTest {

    /**
     * Test that checks that even though independent predicate change distribution information,
     * delta in information obtained by learning new rule is the same.
     * The same is true for delta in kullbackLeibler.
     * This is the main idea of information based rule mining.
     * See [RulesMinerTest.testKL] for check that predicates reduce kullbackLeibler divergence.
     */
    @Test
    fun testIndependenceRuleLearning() {
        val predicates = listOf(
            RangePredicate(10, 20),
            RangePredicate(10, 40),
            RangePredicate(10, 100),
            RangePredicate(20, 40),
            RangePredicate(30, 60),
            RangePredicate(40, 80)
        )
        for (condition in predicates) {
            for (target in predicates.filter { it != condition }) {
                val ps = listOf(condition, target)
                listOf(100, 1000, 100000).forEach { size ->
                    LOG.debug("Database: $size condition: ${condition.name()} target: ${target.name()}")
                    val database = (0.until(size)).toList()
                    val rule = Rule(condition, target, database)
                    val empirical = EmpiricalDistribution(database, ps)
                    val independent = Distribution(database, ps)
                    val learn = independent.learn(rule)
                    val dH = learn.H() - independent.H()
                    val klI = kullbackLeibler(empirical, independent)
                    val klL = kullbackLeibler(empirical, learn)
                    val dKL = klL - klI
                    LOG.debug(
                        "H(Empirical): ${empirical.H()}\tH(Independent): ${independent.H()}\t" +
                                "KL: $klI\tH(rule): ${learn.H()}\tKL(rule): $klL\tdH: $dH\tdKL: $dKL"
                    )
                    Assert.assertEquals("dHdKL", dH, dKL, 1e-10)

                    // Probe predicate
                    val probe = ProbePredicate("probe", database)
                    val empirical2 = EmpiricalDistribution(database, ps + probe)
                    val independent2 = Distribution(database, ps + probe)
                    val learn2 = independent2.learn(rule)
                    val dH2 = learn2.H() - independent2.H()
                    val klL2 = kullbackLeibler(empirical2, learn2)
                    val klI2 = kullbackLeibler(empirical2, independent2)
                    val dKL2 = klL2 - klI2
                    LOG.debug(
                        "H(Empirical(+probe)): ${empirical2.H()}\tH(Independent): ${independent2.H()}\t" +
                                "KL: $klI2\tH(rule): ${learn2.H()}\tKL(rule): $klL2\tdH: $dH2\tdKL: $dKL2"
                    )
                    Assert.assertEquals("dH2", dH, dH2, 1e-10)
                    Assert.assertEquals("dKL2", dKL, dKL2, 1e-10)

                    // Predicate checks that i%3 = 0. Independent with others.
                    val div3 = object : Predicate<Int>() {
                        override fun test(item: Int): Boolean = item % 3 == 0
                        override fun name(): String = "div3"
                    }
                    val empirical3 = EmpiricalDistribution(database, ps + div3)
                    val independent3 = Distribution(database, ps + div3)
                    val learn3 = independent3.learn(rule)
                    val dH3 = learn3.H() - independent3.H()
                    val klL3 = kullbackLeibler(empirical3, learn3)
                    val klI3 = kullbackLeibler(empirical3, independent3)
                    val dKL3 = klL3 - klI3
                    LOG.debug(
                        "H(Empirical(+div3)): ${empirical3.H()}\tH(Independent): ${independent3.H()}\t" +
                                "KL: $klI3\tH(rule): ${learn3.H()}\tKL(rule): $klL3\tdH: $dH3\tdKL: $dKL3"
                    )
                    Assert.assertEquals("dH3", dH, dH3, 1e-10)
                    Assert.assertEquals("dKL3", dKL, dKL3, 1e-10)

                    // Check that we observe the same dKL for dependent predicates
                    // IMPORTANT: Jensen-Shannon distance doesn't work here, otherwise we'll use JSD.
                    // Jensen-Shannon distance is based on the kullbackLeibler divergence, with some notable (and useful) differences,
                    // including that it is symmetric and it is always a finite value.
                    // https://en.wikipedia.org/wiki/Jensen%E2%80%93Shannon_divergence
                    val empirical4 = EmpiricalDistribution(database, ps + ps + probe + div3)
                    val independent4 = Distribution(database, ps + ps + probe + div3)
                    val learn4 = independent4.learn(rule)
                    val dH4 = learn4.H() - independent4.H()
                    val klL4 = kullbackLeibler(empirical4, learn4)
                    val klI4 = kullbackLeibler(empirical4, independent4)
                    val dKL4 = klL4 - klI4
                    LOG.debug(
                        "H(Empirical(x2+probe+div3)): ${empirical4.H()}\tH(Independent): ${independent4.H()}\t" +
                                "KL: $klI4\tH(rule): ${learn4.H()}\tKL(rule): $klL4\tdH: $dH4\tdKL: $dKL4"
                    )
                    Assert.assertEquals("dH4", dH, dH4, 1e-10)
                    Assert.assertEquals("dKL4", dKL, dKL4, 1e-10)
                }
            }
        }
    }

    @Test
    fun testRuleRelearning() {
        val ps = listOf(
            RangePredicate(10, 20),
            RangePredicate(10, 40),
            RangePredicate(10, 100),
            RangePredicate(20, 40),
            RangePredicate(30, 60),
            RangePredicate(40, 80)
        )
        for (condition in ps) {
            for (target in ps) {
                listOf(1000, 10000, 50000).forEach { size ->
                    LOG.debug("Database: $size condition: ${condition.name()} target: ${target.name()}")
                    val database = 0.until(size).toList()
                    val rule = Rule(condition, target, database)
                    val model = Distribution(database, ps)
                    val learn = model.learn(rule)
                    val relearn = learn.learn(rule)
                    Assert.assertEquals(learn.H(), relearn.H(), 1e-10)
                    Assert.assertEquals(0.0, kullbackLeibler(relearn, learn), 1e-10)
                    val klLearn = kullbackLeibler(learn, model)
                    val klRelearn = kullbackLeibler(relearn, model)
                    Assert.assertEquals(klLearn, klRelearn, 1e-10)
                }
            }
        }
    }

    @Test
    fun testProbabilities() {
        val ps = listOf(
            RangePredicate(0, 2),
            RangePredicate(1, 4),
            RangePredicate(1, 10),
            RangePredicate(2, 4),
            RangePredicate(3, 6),
            RangePredicate(4, 8)
        )
        val database = 0.until(10).toList()
        val empirical = EmpiricalDistribution(database, ps)
        Assert.assertEquals(0.0, empirical.probability(encode(0, 1)), 1e-10)
        Assert.assertEquals(0.1, empirical.probability(encode(0, 1, 2)), 1e-10)
        Assert.assertEquals(0.0, empirical.probability(encode(0, 1, 2, 3)), 1e-10)
        Assert.assertEquals(0.2, marginalP(empirical, mapOf(0 to true)), 1e-10)
        Assert.assertEquals(0.3, marginalP(empirical, mapOf(1 to true)), 1e-10)
        Assert.assertEquals(0.2, marginalP(empirical, mapOf(1 to true, 3 to true)), 1e-10)
        Assert.assertEquals(0.1, marginalP(empirical, mapOf(0 to true, 2 to false)), 1e-10)

        val independent = Distribution(database, ps)
        Assert.assertEquals(0.002, independent.probability(encode(0, 1)), 1e-3)
        Assert.assertEquals(0.2, marginalP(independent, mapOf(0 to true)), 1e-10)
        Assert.assertEquals(0.3, marginalP(independent, mapOf(1 to true)), 1e-10)
        Assert.assertEquals(0.06, marginalP(independent, mapOf(1 to true, 3 to true)), 1e-10)
        Assert.assertEquals(0.02, marginalP(independent, mapOf(0 to true, 2 to false)), 1e-10)

    }

    @Test
    fun testKLNonNegative() {
        val predicates = listOf(
            RangePredicate(10, 20),
            RangePredicate(10, 40),
            RangePredicate(10, 100),
            RangePredicate(20, 40),
            RangePredicate(30, 60),
            RangePredicate(40, 80)
        )
        for (condition in predicates) {
            for (target in predicates.filter { it != condition }) {
                val ps = listOf(condition, target)
                listOf(100, 1000, 100000).forEach { size ->
                    LOG.debug("Database: $size condition: ${condition.name()} target: ${target.name()}")
                    val database = (0.until(size)).toList()
                    val rule = Rule(condition, target, database)
                    val empirical = EmpiricalDistribution(database, ps)
                    val independent = Distribution(database, ps)
                    val learn = independent.learn(rule)
                    val kl = kullbackLeibler(empirical, learn)
                    assertTrue(kl >= 0)
                }
            }
        }
    }

    private fun encode(vararg indices: Int): Int {
        return indices.fold(0) { a, b -> a or (1 shl b) }
    }

    private fun <T> marginalP(distribution: Distribution<T>, indices: Map<Int, Boolean>): Double {
        var encoding = 0
        var p = 0.0
        while (encoding < 1 shl distribution.predicates.size) {
            var res = true
            indices.forEach { i, r ->
                res = res && (r xor (encoding and (1 shl i) == 0))
            }
            if (res) {
                p += distribution.probability(encoding)
            }
            encoding++
        }
        return p
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DistributionsTest::class.java)
    }
}