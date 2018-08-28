package org.jetbrains.bio.rules

import junit.framework.TestCase
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.bio.Logs
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.util.time
import java.util.*

class RMTest : TestCase() {

    override fun setUp() {
        Logs.addConsoleAppender(Level.INFO)
    }


    private fun <T> optimizeWithProbes(target: Predicate<T>,
                                       database: List<T>,
                                       conditions: List<Predicate<T>>,
                                       maxComplexity: Int = 10,
                                       topResults: Int = RM.TOP_PER_COMPLEXITY,
                                       convictionDelta: Double = RM.CONVICTION_DELTA,
                                       klDelta: Double = RM.KL_DELTA): RM.Node<T> {
        // 10% of predicates are probes
        val probes = (0..conditions.size / 10).map { ProbePredicate("probe_$it", database) }
        return RM.optimize(conditions + probes, target, database,
                maxComplexity = maxComplexity,
                topPerComplexity = topResults,
                convictionDelta = convictionDelta,
                klDelta = klDelta).first()
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
            val independent = Distribution(database, allAtomics)
            var kl = Double.MAX_VALUE
            for (c in 1..5) {
                val condition = optimizeWithProbes(target, database, predicates, maxComplexity = c).element
                val rule = Rule(condition, target, database)
                val newKL = KL(empiricalDistribution, independent.learn(rule))
                LOG.info("Complexity: $c\tRule: ${rule.name}\tKL: $newKL\tDelta KL: ${newKL - kl}")
                // Fix floating point errors
                assert(newKL <= kl + 1e-10)
                kl = newKL
            }
        }
    }

    private fun <T> RM.Node<T>.structure(database: List<T>): String {
        val result = arrayListOf<String>()
        var node: RM.Node<T>? = this
        val atomics = (rule.conditionPredicate.collectAtomics() + rule.targetPredicate.collectAtomics()).toList()
        val empirical = EmpiricalDistribution(database, atomics)
        val independent = Distribution(database, atomics)
        val kl = KL(empirical, independent)
        while (node != null) {
            result.add("<${node.element.name()}+c${String.format("%.2f", if (node.parent != null)
                node.rule.conviction - node.parent!!.rule.conviction
            else
                node.rule.conviction)}kl${
            String.format("%.2f", KL(empirical, independent.learn(node.rule)) / kl)
            }>")
            node = node.parent
        }
        return result.reversed().joinToString(",")
    }

    fun testOptimizeConvictionDelta() {
        val predicates = (0..5).map { RangePredicate(Math.pow(2.0, it.toDouble()).toInt(), Math.pow(2.0, it.toDouble() + 1).toInt()) }
        val database = (0..100).toList()
        assertEquals("<[32;64)+c6.65kl0.79>,<[16;32)+c3.33kl0.62>,<[8;16)+c1.66kl0.50>,<[4;8)+c0.83kl0.42>,<[2;4)+c0.42kl0.38>,<[1;2)+c0.21kl0.35>",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 1E-2, klDelta = 1E-2).structure(database))
        val r0 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 1E-2, klDelta = -1.0)
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)", r0.rule.conditionPredicate.name())
        assertEquals("<[32;64)+c6.65kl0.79>,<[16;32)+c3.33kl0.62>,<[8;16)+c1.66kl0.50>,<[4;8)+c0.83kl0.42>,<[2;4)+c0.42kl0.38>,<[1;2)+c0.21kl0.35>",
                r0.structure(database))
        val r1 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 1.0, klDelta = -1.0)
        assertEquals("[16;32) OR [32;64) OR [4;8) OR [8;16)", r1.rule.conditionPredicate.name())
        assertEquals("<[4;8)+c0.83kl0.98>,<[32;64)+c6.65kl0.72>,<[16;32)+c3.33kl0.51>,<[8;16)+c1.66kl0.35>", r1.structure(database))
        val r2 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 2.0, klDelta = -1.0)
        assertEquals("[16;32) OR [32;64) OR [8;16)", r2.rule.conditionPredicate.name())
        assertEquals("<[8;16)+c1.66kl0.94>,<[32;64)+c6.65kl0.62>,<[16;32)+c3.33kl0.33>", r2.structure(database))
        val r5 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 5.0, klDelta = -1.0)
        assertEquals("[16;32) OR [32;64)", r5.rule.conditionPredicate.name())
        assertEquals("<[16;32)+c3.33kl0.82>,<[32;64)+c6.65kl0.27>", r5.structure(database))
        val r10 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 10.0, klDelta = -1.0)
        assertEquals("[32;64)", r10.rule.conditionPredicate.name())
        assertEquals("<[32;64)+c6.65kl0.00>", r10.structure(database))
    }

    fun testOptimizeKLDelta() {
        val predicates = (0..5).map { RangePredicate(Math.pow(2.0, it.toDouble()).toInt(), Math.pow(2.0, it.toDouble() + 1).toInt()) }
        val database = (0..100).toList()
        assertEquals("<[32;64)+c6.65kl0.79>,<[16;32)+c3.33kl0.62>,<[8;16)+c1.66kl0.50>,<[4;8)+c0.83kl0.42>,<[2;4)+c0.42kl0.38>,<[1;2)+c0.21kl0.35>",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 1E-2).structure(database))
        assertEquals("<[2;4)+c0.42kl0.99>,<[32;64)+c6.65kl0.76>,<[4;8)+c0.83kl0.72>,<[16;32)+c3.33kl0.51>,<[8;16)+c1.66kl0.35>",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.1).structure(database))
        assertEquals("<[1;2)+c0.21kl0.99>,<[2;4)+c0.42kl0.97>,<[16;32)+c3.33kl0.81>,<[32;64)+c6.65kl0.30>",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.5).structure(database))
    }

    fun testOptimize() {
        val predicates = predicates(10, 100)
        listOf(100, 1000, 5000).forEach { size ->
            val database = 0.until(size).toList()
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50)",
                    optimizeWithProbes(RangePredicate(20, 50), database, predicates).rule.conditionPredicate.name())
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50) OR [50;60)",
                    optimizeWithProbes(RangePredicate(20, 60), database, predicates).rule.conditionPredicate.name())
            assertEquals(size.toString(), "[20;30) OR [30;40) OR [40;50)",
                    optimizeWithProbes(RangePredicate(20, 51), database, predicates).rule.conditionPredicate.name())
        }
    }


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
            val correctOrderRule = RM.optimize(order, target, database, maxComplexity = 20).first().rule
            LOG.info("Rule correct order ${correctOrderRule.name}")
            if (correctOrderRule.conviction > bestRule.conviction) {
                fail("Best rule is not optimal.")
            }

            LOG.time(level = Level.INFO, message = "RM") {
                for (top in 1..maxTop) {
                    val rule = RM.optimize(order, target, database, maxComplexity = 20,
                            topPerComplexity = maxTop).first().rule
                    LOG.debug("RuleNode (top=$top): ${rule.name}")
                    assertTrue(rule.conviction >= bestRule.conviction)
                }
            }
        }
    }


    fun testCorrectOrder() {
        val predicates = listOf(RangePredicate(20, 35), RangePredicate(35, 48)) +
                0.until(5).map { RangePredicate(it * 10, (it + 1) * 10) }
        val database = 0.until(100).toList()
        val optimize = RM.optimize(predicates, RangePredicate(20, 50), database, maxComplexity = 3, topPerComplexity = 3)
        assertEquals(listOf("[20;35) OR [35;48) OR [40;50)", "[20;35) OR [30;40) OR [40;50)", "[20;35) OR [35;48)"),
                optimize.take(3).map { it.rule.conditionPredicate.name() })
        assertEquals(3 * 3, optimize.size)
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