package org.jetbrains.bio.rules

import junit.framework.TestCase
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.bio.Logs
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.util.time
import org.junit.Test
import java.util.*

class RMTest : TestCase() {

    override fun setUp() {
        Logs.addConsoleAppender(Level.INFO)
    }


    private fun <T> optimizeWithProbes(target: Predicate<T>,
                                       database: List<T>,
                                       conditions: List<Predicate<T>>,
                                       maxComplexity: Int = 10,
                                       topResults: Int = 10,
                                       convictionDelta: Double = 1e-2,
                                       klDelta: Double = 1e-2): Predicate<T> {
        // 20% of predicates are probes
        val probes = (0..conditions.size / 5).map { ProbePredicate("probe_$it", database) }
        return RM.optimize(conditions + probes, target, database,
                maxComplexity = maxComplexity,
                topResults = topResults,
                convictionDelta = convictionDelta,
                klDelta = klDelta).first().rule.conditionPredicate
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
                val condition = optimizeWithProbes(target, database, predicates, maxComplexity = c)
                val rule = Rule(condition, target, database)
                val newKL = KL(empiricalDistribution, Distribution(database, allAtomics).learn(rule))
                LOG.info("Complexity: $c\tRule: ${rule.name}\tKL: $newKL\tDelta KL: ${newKL - kl}")
                // Fix floating point errors
                assert(newKL <= kl + 1e-10)
                kl = newKL
            }
        }
    }

    fun testOptimizeConvictionDelta() {
        val predicates = (0..5).map { RangePredicate(Math.pow(2.0, it.toDouble()).toInt(), Math.pow(2.0, it.toDouble() + 1).toInt()) }
        val database = (0..100).toList()
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = -1.0).name())
        assertEquals("[16;32) OR [32;64) OR [8;16)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 1.0, klDelta = -1.0).name())
        assertEquals("[16;32) OR [32;64)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 5.0, klDelta = -1.0).name())
        assertEquals("[32;64)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 10.0, klDelta = -1.0).name())
    }

    fun testOptimizeKLDelta() {
        val predicates = (0..5).map { RangePredicate(Math.pow(2.0, it.toDouble()).toInt(), Math.pow(2.0, it.toDouble() + 1).toInt()) }
        val database = (0..100).toList()
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.0).name())
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 1e-2).name())
        assertEquals("[16;32) OR [32;64)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.1).name())
        assertEquals("[32;64)",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.2).name())
    }


    fun testOptimize() {
        val predicates = predicates(10, 100)
        listOf(100, 1000, 5000).forEach { size ->
            val database = 0.until(size).toList()
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50)",
                    optimizeWithProbes(RangePredicate(20, 50), database, predicates).name())
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50) OR [50;60)",
                    optimizeWithProbes(RangePredicate(20, 60), database, predicates).name())
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50)",
                    optimizeWithProbes(RangePredicate(20, 51), database, predicates).name())
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
            for (i in 0 until p - 1) {
                order.add(RangePredicate(-10000 + 100 * i, -10000 + predicates * 100 + 100 * i)
                        .or(RangePredicate(-120, 100))
                        .or(RangePredicate(100 + 100 * i, 200 + 100 * i)).named("X${i + 1}"))
            }
            order.add(RangePredicate(-120, 120).named("Z"))
            val solutionX = Predicate.and(order.subList(0, order.size - 1))
            val bestRule = Rule(solutionX, target, database)
            LOG.info("Best rule ${bestRule.name}")
            val correctOrderRule = RM.optimize(order, target, database, maxComplexity = 20,
                    topResults = 100, convictionDelta = 1e-1, klDelta = 1e-1).first().rule
            LOG.info("Rule correct order ${correctOrderRule.name}")
            if (correctOrderRule.conviction > bestRule.conviction) {
                fail("Best rule is not optimal.")
            }

            LOG.time(level = Level.INFO, message = "RM") {
                for (top in 1..maxTop) {
                    val rule = RM.optimize(order, target, database, maxComplexity = 20,
                            topResults = maxTop, convictionDelta = 1e-1, klDelta = 1e-2).first().rule
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