package org.jetbrains.bio.rules

import junit.framework.TestCase
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.predicates.ProbePredicate
import org.jetbrains.bio.rules.Distribution.Companion.kullbackLeibler
import org.jetbrains.bio.util.time
import org.junit.Test
import java.util.*

class RulesMinerTest : TestCase() {

    private fun <T> optimizeWithProbes(target: Predicate<T>,
                                       database: List<T>,
                                       conditions: List<Predicate<T>>,
                                       maxComplexity: Int = 10,
                                       topPerComplexity: Int = RulesMiner.TOP_PER_COMPLEXITY,
                                       topLevelToPredicatesInfo: Int = RulesMiner.TOP_LEVEL_PREDICATES_INFO,
                                       convictionDelta: Double = RulesMiner.CONVICTION_DELTA,
                                       klDelta: Double = RulesMiner.KL_DELTA): RulesMiner.Node<T> {
        // 10% of predicates are probes
        val probes = (0..conditions.size / 10).map { ProbePredicate("probe_$it", database) }
        return RulesMiner.mine(conditions + probes, target, database,
                maxComplexity = maxComplexity,
                topPerComplexity = topPerComplexity,
                topLevelToPredicatesInfo = topLevelToPredicatesInfo,
                convictionDelta = convictionDelta,
                klDelta = klDelta).first()
    }

    private fun predicates(number: Int, dataSize: Int): List<Predicate<Int>> {
        return 0.until(number).map { RangePredicate(it * dataSize / number, (it + 1) * dataSize / number) }
    }

    @Test
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
                val newKL = kullbackLeibler(empiricalDistribution, independent.learn(rule))
                LOG.debug("Complexity: $c\tRule: ${rule.name}\tKL: $newKL\tDelta KL: ${newKL - kl}")
                // Fix floating point errors
                assert(newKL <= kl + 1e-10)
                kl = newKL
            }
        }
    }

    private fun <T> RulesMiner.Node<T>.structure(database: List<T>,
                                                 logConviction: Boolean = true,
                                                 logKL: Boolean = true): String {
        val result = arrayListOf<String>()
        var node: RulesMiner.Node<T>? = this
        val atomics = (rule.conditionPredicate.collectAtomics() + rule.targetPredicate.collectAtomics()).toList()
        val empirical = EmpiricalDistribution(database, atomics)
        val independent = Distribution(database, atomics)
        val kl = kullbackLeibler(empirical, independent)
        while (node != null) {
            result.add("<${node.element.name()}" +
                    (if (logConviction) "+c${String.format("%.2f", if (node.parent != null)
                        node.rule.conviction - node.parent!!.rule.conviction
                    else
                        node.rule.conviction)}" else "") +
                    (if (logKL) "kl${
                    String.format("%.2f", kullbackLeibler(empirical, independent.learn(node.rule)) / kl)
                    }" else "") + ">")
            node = node.parent
        }
        return result.reversed().joinToString(",")
    }

    fun testOptimizationStructureDefaultParams() {
        val predicates = (0..5).map {
            RangePredicate(Math.pow(2.0, it.toDouble()).toInt(), Math.pow(2.0, it.toDouble() + 1).toInt())
        }
        val database = (0..100).toList()
        val o = RulesMiner.mineByComplexity(predicates, RangePredicate(0, 80),
                database, maxComplexity = 6, topPerComplexity = 3)
        assertEquals("[32;64), [16;32), [8;16)",
                o[1].reversed().joinToString(", ") { it.rule.conditionPredicate.name() })
        assertEquals("[16;32) OR [32;64), [32;64) OR [8;16), [32;64) OR [4;8)",
                o[2].reversed().joinToString(", ") { it.rule.conditionPredicate.name() })
        assertEquals("[16;32) OR [32;64) OR [8;16), [16;32) OR [32;64) OR [4;8), [16;32) OR [2;4) OR [32;64)",
                o[3].reversed().joinToString(", ") { it.rule.conditionPredicate.name() })
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)",
                o[6].reversed().joinToString(", ") { it.rule.conditionPredicate.name() })
        assertEquals("<[32;64)+c6.65kl0.79>,<[16;32)+c3.33kl0.62>,<[8;16)+c1.66kl0.50>,<[4;8)+c0.83kl0.42>,<[2;4)+c0.42kl0.38>,<[1;2)+c0.21kl0.35>",
                o[6].single().structure(database))
    }

    @Test
    fun testOptimizeConvictionDelta() {
        val predicates = (0..5).map { RangePredicate(Math.pow(2.0, it.toDouble()).toInt(), Math.pow(2.0, it.toDouble() + 1).toInt()) }
        val database = (0..100).toList()
        val r0 = optimizeWithProbes(RangePredicate(0, 80), database, predicates,
                convictionDelta = RulesMiner.CONVICTION_DELTA, klDelta = -1.0)
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)", r0.rule.conditionPredicate.name())
        assertEquals("<[32;64)+c6.65kl0.79>,<[16;32)+c3.33kl0.62>,<[8;16)+c1.66kl0.50>,<[4;8)+c0.83kl0.42>,<[2;4)+c0.42kl0.38>,<[1;2)+c0.21kl0.35>",
                r0.structure(database))
        val r05 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.5, klDelta = -1.0)
        assertEquals("[16;32) OR [32;64) OR [4;8) OR [8;16)", r05.rule.conditionPredicate.name())
        assertEquals("<[32;64)+c6.65kl0.76>,<[16;32)+c3.33kl0.57>,<[8;16)+c1.66kl0.43>,<[4;8)+c0.83kl0.35>", r05.structure(database))
        val r1 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 1.0, klDelta = -1.0)
        assertEquals("[16;32) OR [32;64) OR [8;16)", r1.rule.conditionPredicate.name())
        assertEquals("<[32;64)+c6.65kl0.72>,<[16;32)+c3.33kl0.49>,<[8;16)+c1.66kl0.33>", r1.structure(database))
        val r2 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 2.0, klDelta = -1.0)
        assertEquals("[16;32) OR [32;64)", r2.rule.conditionPredicate.name())
        assertEquals("<[32;64)+c6.65kl0.60>,<[16;32)+c3.33kl0.27>", r2.structure(database))
        val r10 = optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 10.0, klDelta = -1.0)
        assertEquals("[32;64)", r10.rule.conditionPredicate.name())
        assertEquals("<[32;64)+c6.65kl0.00>", r10.structure(database))
    }

    @Test
    fun testOptimizeKLDelta() {
        val predicates = (0..5).map { RangePredicate(Math.pow(2.0, it.toDouble()).toInt(), Math.pow(2.0, it.toDouble() + 1).toInt()) }
        var database = (0..100).toList()
        assertEquals("<[32;64)+c6.65kl0.79>,<[16;32)+c3.33kl0.62>,<[8;16)+c1.66kl0.50>,<[4;8)+c0.83kl0.42>,<[2;4)+c0.42kl0.38>,<[1;2)+c0.21kl0.35>",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = RulesMiner.KL_DELTA).structure(database))
        // NOTE that [4;8) is placed before [16;32), this is result of high kullbackLeibler delta
        assertEquals("<[32;64)+c6.65kl0.76>,<[4;8)+c0.83kl0.72>,<[16;32)+c3.33kl0.51>,<[8;16)+c1.66kl0.35>",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.1).structure(database))
        assertEquals("<[32;64)+c6.65kl0.00>",
                optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.5).structure(database))
        // Check that exact values can be slightly different overall optimization results persist
        listOf(500, 1000, 100000).forEach { size ->
            database = (0..size).toList()
            assertEquals("<[32;64)>,<[16;32)>,<[8;16)>,<[4;8)>,<[2;4)>,<[1;2)>",
                    optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = RulesMiner.KL_DELTA)
                            .structure(database, logConviction = false, logKL = false))
            // NOTE that [4;8) is placed before [16;32), this is result of high kullbackLeibler delta
            assertEquals("<[32;64)>,<[4;8)>,<[16;32)>,<[8;16)>",
                    optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.1)
                            .structure(database, logConviction = false, logKL = false))
            assertEquals("<[32;64)>",
                    optimizeWithProbes(RangePredicate(0, 80), database, predicates, convictionDelta = 0.0, klDelta = 0.5)
                            .structure(database, logConviction = false, logKL = false))
        }
    }

    @Test
    fun testOptimize() {
        val predicates = predicates(10, 100)
        listOf(100, 1000, 5000).forEach { size ->
            val database = (0..size).toList()
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
            val correctOrderRule = RulesMiner.mine(order, target, database, maxComplexity = 20).first().rule
            LOG.info("Rule correct order ${correctOrderRule.name}")
            if (correctOrderRule.conviction > bestRule.conviction) {
                fail("Best rule is not optimal.")
            }

            LOG.time(level = Level.INFO, message = "RM") {
                for (top in 1..maxTop) {
                    val rule = RulesMiner.mine(order, target, database, maxComplexity = 20,
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
        val optimize = RulesMiner.mine(predicates, RangePredicate(20, 50), database, maxComplexity = 3, topPerComplexity = 3)
        assertEquals(listOf("[20;35) OR [35;48) OR [40;50)", "[20;35) OR [30;40) OR [40;50)", "[20;35) OR [35;48)"),
                optimize.take(3).map { it.rule.conditionPredicate.name() })
        assertEquals(3 * 3, optimize.size)
    }


    companion object {
        internal val LOG = Logger.getLogger(RulesMinerTest::class.java)
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
    RulesMinerTest.LOG.info("$name = ${this.name()}")
    return object : Predicate<T>() {
        override fun test(item: T) = this@named.test(item)
        override fun name() = name
    }
}
