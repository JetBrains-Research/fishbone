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
     * See [RMLongTest.testKL] for check that predicates reduce KL divergence.
     */
    @Test fun testIndependenceRuleLearning() {
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
                    val database = (0.until(size)).toList()
                    val rule = Rule(condition, target, database)
                    val empirical = EmpiricalDistribution(database, ps)
                    val model = Distribution(database, ps)
                    val learn = model.learn(rule)
                    val dI = learn.H() - model.H()
                    val dKL = KL(empirical, learn) - KL(empirical, model)
                    println("Model: ${model.H()}\trule: ${learn.H()}\tempirical: ${empirical.H()}\tdI: $dI\tdKL: $dKL")

                    // Predicate checks that i is even. Independent with others.
                    val even = object : Predicate<Int>() {
                        override fun test(item: Int): Boolean = item % 2 == 0
                        override fun name(): String = "even"
                    }
                    val empirical2 = EmpiricalDistribution(database, ps + even)
                    val model2 = Distribution(database, ps + even)
                    val learn2 = model2.learn(rule)
                    val dI2 = learn2.H() - model2.H()
                    val dKL2 = KL(empirical2, learn2) - KL(empirical2, model2)
                    println("Model + even: ${model2.H()}\trule: ${learn2.H()}\tempirical: ${empirical2.H()}\tdI: $dI2\tdKL: $dKL2")
                    Assert.assertEquals("dI2", dI, dI2, 1e-10)
                    Assert.assertEquals("dKL2", dKL, dKL2, 1e-10)

                    // Predicate checks that i%3 = 0. Independent with others.
                    val div3 = object : Predicate<Int>() {
                        override fun test(item: Int): Boolean = item % 3 == 0
                        override fun name(): String = "div3"
                    }
                    val empirical3 = EmpiricalDistribution(database, ps + div3)
                    val model3 = Distribution(database, ps + div3)
                    val learn3 = model3.learn(rule)
                    val dI3 = learn3.H() - model3.H()
                    val dKL3 = KL(empirical3, learn3) - KL(empirical3, model3)
                    println("Model + div3: ${model3.H()}\trule: ${learn3.H()}\tempirical: ${empirical3.H()}\tdI: $dI3\tdKL: $dKL3")
                    Assert.assertEquals("dI3", dI, dI3, 1e-10)
                    Assert.assertEquals("dKL3", dKL, dKL3, 1e-10)

                    // Check that we observe the same dKL for related predicates
                    // IMPORTANT: Jensen-Shannon distance doesn't work here, otherwise we'll use JSD.
                    // Jensen-Shannon distance is based on the KL divergence, with some notable (and useful) differences,
                    // including that it is symmetric and it is always a finite value.
                    // https://en.wikipedia.org/wiki/Jensen%E2%80%93Shannon_divergence
                    val empirical4 = EmpiricalDistribution(database, ps + ps + even + div3)
                    val model4 = Distribution(database, ps + ps + even + div3)
                    val learn4 = model4.learn(rule)
                    val dI4 = learn4.H() - model4.H()
                    val dKL4 = KL(empirical4, learn4) - KL(empirical4, model4)
                    println("Model x2: ${model4.H()}\trule: ${learn4.H()}\tempirical: ${empirical4.H()}\tdI: $dI4\tdKL: $dKL4")
                    Assert.assertEquals("dI4", dI, dI4, 1e-10)
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