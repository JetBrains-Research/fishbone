package org.jetbrains.bio.rules

import junit.framework.TestCase
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.bio.Logs
import org.jetbrains.bio.predicates.AndPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.statistics.distribution.Sampling
import org.jetbrains.bio.util.time
import org.junit.Test
import java.util.*

class RMTest : TestCase() {

    override fun setUp() {
        Logs.addConsoleAppender(Level.INFO)
    }

    /**
     * Probe predicate is used as an evaluation of rule mining algorithm as an effective feature selection
     * “Causal Feature Selection” I. Guyon et al., "Computational Methods of Feature Selection", 2007.
     * http://clopinet.com/isabelle/Papers/causalFS.pdf
     */
    private class ProbePredicate<T>(private val name: String, database: List<T>) : Predicate<T>() {
        // 0.5 probability is a good idea, because of zero information,
        // each subset is going to have similar coverage fraction.
        private val trueSet = database.filter { Sampling.sampleBernoulli(0.5) }.toSet()

        override fun test(item: T): Boolean = item in trueSet
        override fun name() = name
    }

    private fun <T> optimize(target: Predicate<T>,
                             database: List<T>,
                             conditions: List<Predicate<T>>,
                             maxComplexity: Int = 10): Predicate<T> {
        // 10% of predicates are probes
        val probes = (0 until (conditions.size * 0.1).toInt()).map { ProbePredicate("probe_$it", database) }
        return RM.optimize(conditions + probes, target, database,
                maxComplexity = maxComplexity,
                topResults = 10, convictionDelta = 1e-3, klDelta = 1e-3).first().rule.conditionPredicate
    }

    private fun predicates(number: Int, dataSize: Int): List<Predicate<Int>> {
        return 0.until(number).map { RangePredicate(it * dataSize / number, (it + 1) * dataSize / number) }
    }

    fun testKL() {
        val predicates = predicates(10, 100)
        listOf(100, 1000, 5000).forEach { size ->
            val database = (0.until(size)).toList()
            val target = RangePredicate(10, 40)
            val allAtomics = predicates + target
            val empiricalDistribution = EmpiricalDistribution(database, allAtomics)
            var kl = Double.MAX_VALUE
            for (c in 1..5) {
                val condition = optimize(target, database, predicates, maxComplexity = c)
                val rule = Rule(condition, target, database)
                val newKL = KL(empiricalDistribution, Distribution(database, allAtomics).learn(rule))
                LOG.info("Complexity: $c\tRule: ${rule.name}\tKL: $newKL\tDelta KL: ${newKL - kl}")
                // Fix floating point errors
                assert(newKL <= kl + 1e-10)
                kl = newKL
            }
        }
    }

    fun testOptimize() {
        val predicates = predicates(10, 100)
        listOf(100, 1000, 5000).forEach { size ->
            val database = 0.until(size).toList()
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50)",
                    optimize(RangePredicate(20, 50), database, predicates).name())
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50) OR [50;60)",
                    optimize(RangePredicate(20, 60), database, predicates).name())
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50)",
                    optimize(RangePredicate(20, 51), database, predicates).name())
        }
    }


    @Test
    fun testCounterExamples() {
        val target: Predicate<Int> = RangePredicate(-100, 100).named("T")
        val maxTop = 5
        val predicates = 10
        for (p in 3..predicates) {
            LOG.info("Standard score function and predicates: $p")
            val X0 = RangePredicate(-100, 1000).named("X0")
            val order = arrayListOf(X0)
            val database = IntRange(-10000, 10000).toList()
            val ORDER = order
            for (i in 0 until p - 1) {
                order.add(RangePredicate(-10000 + 100 * i, -10000 + predicates * 100 + 100 * i)
                        .or(RangePredicate(-120, 100))
                        .or(RangePredicate(100 + 100 * i, 200 + 100 * i)).named("X${i + 1}"))
            }
            order.add(RangePredicate(-120, 120).named("Z"))
            val solutionX = AndPredicate.of(ORDER.subList(0, ORDER.size - 1))
            val bestRule = Rule(solutionX, target, database)
            LOG.info("Best rule ${bestRule.name}")
            val correctOrderRule = RM.optimize(ORDER, target, database, maxComplexity = 20,
                    topResults = 100, convictionDelta = 1e-3, klDelta = 1e-3).first().rule
            LOG.info("Rule correct order ${correctOrderRule.name}")
            if (correctOrderRule.conviction > bestRule.conviction) {
                fail("Best rule is not optimal.")
            }

            LOG.time(level = Level.INFO, message = "RM") {
                for (top in 1..maxTop) {
                    val rule = RM.optimize(ORDER, target, database, maxComplexity = 20,
                            topResults = maxTop, convictionDelta = 1e-3, klDelta = 1e-3).first().rule
                    LOG.debug("RuleNode (top=$top): ${rule.name}")
                    assertTrue(rule.conviction >= bestRule.conviction)
                }
            }
        }
    }


    @Test
    fun testCorrectOrder() {
        val predicates = listOf(RangePredicate(20, 35), RangePredicate(35, 48)) +
                0.until(5).map { RangePredicate(it * 10, (it + 1) * 10) }
        val database = 0.until(100).toList()
        val result = arrayListOf<String>()
        RM.mine("foo", database, listOf(predicates to RangePredicate(20, 50)),
                { it.forEach { result.add(it.rule.conditionPredicate.name()) } }, maxComplexity = 3, topResults = 3)
        assertEquals(listOf("[20;35) OR [35;48) OR [40;50)", "[20;35) OR [30;40) OR [40;50)", "[20;35) OR [35;48)"),
                result)
    }


    companion object {
        internal val LOG = Logger.getLogger(RMTest::class.java)
    }
}



class RangePredicate(private val start: Int, private val end: Int) : Predicate<Int>() {

    override fun test(item: Int): Boolean = item in start..(end - 1)

    override fun name(): String = "[$start;$end)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RangePredicate) return false
        return start == other.start && end == other.end
    }

    override fun hashCode(): Int = Objects.hash(start, end)
}

fun <T> Predicate<T>.named(name: String): Predicate<T> {
    RMTest.LOG.info("$name = ${this.name()}")
    return object : Predicate<T>() {
        override fun test(item: T) = this@named.test(item)
        override fun name() = name
    }
}