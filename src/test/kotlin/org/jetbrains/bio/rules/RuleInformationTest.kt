package org.jetbrains.bio.rules

import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.statistics.data.BitterSet
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals

class RuleInformationTest {

    /**
     * Test that checks that even though independent predicate change distribution information,
     * delta in information obtained by learning new rule is the same.
     * The same is true for delta in KL.
     * This is the main idea of information based rule mining.
     * See [RMTest.testKL] for check that predicates reduce KL divergence.
     */
    @Test fun testIndependenceRuleLearning() {
        val predicates = listOf(
                RangePredicate(10, 20),
                RangePredicate(10, 40),
                RangePredicate(10, 100),
                RangePredicate(20, 40),
                RangePredicate(30, 60),
                RangePredicate(40, 80))
        for (condition in predicates) {
            for (target in predicates.filter { it != condition }) {
                val ps = listOf(condition, target)
                listOf(100, 1000, 100000).forEach { size ->
                    println("Database: $size condition: ${condition.name()} target: ${target.name()}")
                    val database = (0.until(size)).toList()
                    val rule = Rule(condition, target, database)
                    val empirical = EmpiricalDistribution(database, ps)
                    val independent = Distribution(database, ps)
                    val learn = independent.learn(rule)
                    val dH = learn.H() - independent.H()
                    val klI = KL(empirical, independent)
                    val klL = KL(empirical, learn)
                    val dKL = klL - klI
                    println("H(Empirical): ${empirical.H()}\tH(Independent): ${independent.H()}\tKL: $klI\tH(rule): ${learn.H()}\tKL(rule): $klL\tdH: $dH\tdKL: $dKL")
                    Assert.assertEquals("dHdKL", dH, dKL, 1e-10)

                    // Probe predicate
                    val probe = ProbePredicate("probe", database)
                    val empirical2 = EmpiricalDistribution(database, ps + probe)
                    val independent2 = Distribution(database, ps + probe)
                    val learn2 = independent2.learn(rule)
                    val dH2 = learn2.H() - independent2.H()
                    val klL2 = KL(empirical2, learn2)
                    val klI2 = KL(empirical2, independent2)
                    val dKL2 = klL2 - klI2
                    println("H(Empirical(+probe)): ${empirical2.H()}\tH(Independent): ${independent2.H()}\tKL: $klI2\tH(rule): ${learn2.H()}\tKL(rule): $klL2\tdH: $dH2\tdKL: $dKL2")
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
                    val klL3 = KL(empirical3, learn3)
                    val klI3 = KL(empirical3, independent3)
                    val dKL3 = klL3 - klI3
                    println("H(Empirical(+div3)): ${empirical3.H()}\tH(Independent): ${independent3.H()}\tKL: $klI3\tH(rule): ${learn3.H()}\tKL(rule): $klL3\tdH: $dH3\tdKL: $dKL3")
                    Assert.assertEquals("dH3", dH, dH3, 1e-10)
                    Assert.assertEquals("dKL3", dKL, dKL3, 1e-10)

                    // Check that we observe the same dKL for dependent predicates
                    // IMPORTANT: Jensen-Shannon distance doesn't work here, otherwise we'll use JSD.
                    // Jensen-Shannon distance is based on the KL divergence, with some notable (and useful) differences,
                    // including that it is symmetric and it is always a finite value.
                    // https://en.wikipedia.org/wiki/Jensen%E2%80%93Shannon_divergence
                    val empirical4 = EmpiricalDistribution(database, ps + ps + probe + div3)
                    val independent4 = Distribution(database, ps + ps + probe + div3)
                    val learn4 = independent4.learn(rule)
                    val dH4 = learn4.H() - independent4.H()
                    val klL4 = KL(empirical4, learn4)
                    val klI4 = KL(empirical4, independent4)
                    val dKL4 = klL4 - klI4
                    println("H(Empirical(x2+probe+div3)): ${empirical4.H()}\tH(Independent): ${independent4.H()}\tKL: $klI4\tH(rule): ${learn4.H()}\tKL(rule): $klL4\tdH: $dH4\tdKL: $dKL4")
                    Assert.assertEquals("dH4", dH, dH4, 1e-10)
                    Assert.assertEquals("dKL4", dKL, dKL4, 1e-10)
                }
            }
        }
    }

    @Test fun testRuleRelearning() {
        val ps = listOf(
                RangePredicate(10, 20),
                RangePredicate(10, 40),
                RangePredicate(10, 100),
                RangePredicate(20, 40),
                RangePredicate(30, 60),
                RangePredicate(40, 80))
        for (condition in ps) {
            for (target in ps) {
                listOf(1000, 10000, 50000).forEach { size ->
                    println("Database: $size condition: ${condition.name()} target: ${target.name()}")
                    val database = 0.until(size).toList()
                    val rule = Rule(condition, target, database)
                    val model = Distribution(database, ps)
                    val learn = model.learn(rule)
                    val relearn = learn.learn(rule)
                    Assert.assertEquals(learn.H(), relearn.H(), 1e-10)
                    Assert.assertEquals(0.0, KL(relearn, learn), 1e-10)
                    val klLearn = KL(learn, model)
                    val klRelearn = KL(relearn, model)
                    Assert.assertEquals(klLearn, klRelearn, 1e-10)
                }
            }
        }
    }

}

class BitSetNextTest {
    @Test
    fun zero() {
        BitterSet(8).apply {
            next(this)
            assertEquals(BitterSet(8) { it == 7 }, this)
        }
    }
}