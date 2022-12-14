package org.jetbrains.bio.fishbone.miner

import junit.framework.TestCase
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.ProbePredicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.jetbrains.bio.fishbone.rule.distribution.Distribution
import org.jetbrains.bio.fishbone.rule.distribution.Distribution.Companion.kullbackLeibler
import org.jetbrains.bio.fishbone.rule.distribution.EmpiricalDistribution
import org.jetbrains.bio.statistics.distribution.Sampling
import org.jetbrains.bio.util.time
import org.junit.Test
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.*
import kotlin.math.pow
import kotlin.reflect.KProperty1

class RulesMinerTest : TestCase() {

    private fun <T> optimizeWithProbes(
        target: Predicate<T>,
        database: List<T>,
        conditions: List<Predicate<T>>,
        maxComplexity: Int = 10,
        topPerComplexity: Int = FishboneMiner.TOP_PER_COMPLEXITY,
        function: (Rule<T>) -> Double,
        functionDelta: Double = FishboneMiner.FUNCTION_DELTA,
        klDelta: Double = FishboneMiner.KL_DELTA
    ): FishboneMiner.Node<T> {
        // Fix seed for tests reproducibility
        Sampling.RANDOM_DATA_GENERATOR.reSeed(42)
        // 10% of predicates are probes
        val probes = (0..conditions.size / 10).map { ProbePredicate("probe_$it", database) }
        return FishboneMiner.mine(
            conditions + probes, target, database,
            maxComplexity = maxComplexity,
            topPerComplexity = topPerComplexity,
            function = function,
            functionDelta = functionDelta,
            klDelta = klDelta
        ).first()
    }

    private fun predicates(number: Int, dataSize: Int): List<Predicate<Int>> {
        return 0.until(number).map { RangePredicate(it * dataSize / number, (it + 1) * dataSize / number) }
    }

    private fun <T> FishboneMiner.Node<T>.structure(
        database: List<T>,
        logConviction: Boolean = true,
        logKL: Boolean = true,
        function: (Rule<T>) -> Double
    ): String {
        val result = arrayListOf<String>()
        var node: FishboneMiner.Node<T>? = this
        val atomics = (rule.conditionPredicate.collectAtomics() + rule.targetPredicate.collectAtomics()).toList()
        val empirical = EmpiricalDistribution(database, atomics)
        val independent = Distribution(database, atomics)
        val kl = kullbackLeibler(empirical, independent)
        while (node != null) {
            result.add(
                "${node.element.name()}(" +
                        (if (logConviction) String.format("%.2f", function(node.rule)) else "") +
                        (if (logKL) ",${
                            String.format(
                                "%.2f",
                                kullbackLeibler(empirical, independent.learn(node.rule)) / kl
                            )
                        }" else "") +
                        ")"
            )
            node = node.parent
        }
        return result.reversed().joinToString(",")
    }

    @Test
    fun testKLConviction() {
        testKL(Rule<Int>::conviction)
    }

    @Test
    fun testKLLOE() {
        testKL(Rule<Int>::loe)
    }

    private fun testKL(function: KProperty1<Rule<Int>, Double>) {
        val predicates = predicates(10, 100)
        listOf(100, 1000, 5000).forEach { size ->
            val database = (0.until(size)).toList()
            val target = RangePredicate(10, 40)
            val allAtomics = predicates + target
            val empiricalDistribution = EmpiricalDistribution(database, allAtomics)
            val independent = Distribution(database, allAtomics)
            var kl = Double.MAX_VALUE
            for (c in 1..5) {
                val condition =
                    optimizeWithProbes(target, database, predicates, maxComplexity = c, function = function).element
                val rule = Rule(condition, target, database)
                val newKL = kullbackLeibler(empiricalDistribution, independent.learn(rule))
                LOG.debug("Complexity: $c\tRule: ${rule.name}\tKL: $newKL\tDelta KL: ${newKL - kl}")
                // Fix floating point errors
                assert(newKL <= kl + 1e-10)
                kl = newKL
            }
        }
    }


    @Test
    fun testOptimizeConvictionThresholds() {
        val predicates =
            (0..5).map { RangePredicate(2.0.pow(it.toDouble()).toInt(), 2.0.pow(it.toDouble() + 1).toInt()) }
        val database = (0..100).toList()

        val r0 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::conviction, functionDelta = FishboneMiner.FUNCTION_DELTA, klDelta = -1.0
        )
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)", r0.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(6.65,0.79),[16;32)(9.98,0.62),[8;16)(11.64,0.50),[4;8)(12.48,0.42),[2;4)(12.89,0.38),[1;2)(13.10,0.35)",
            r0.structure(database, function = Rule<Int>::conviction)
        )

        val r05 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::conviction, functionDelta = 0.5, klDelta = -1.0
        )
        assertEquals("[16;32) OR [32;64) OR [4;8) OR [8;16)", r05.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(6.65,0.76),[16;32)(9.98,0.57),[8;16)(11.64,0.43),[4;8)(12.48,0.35)",
            r05.structure(database, function = Rule<Int>::conviction)
        )

        val r1 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::conviction, functionDelta = 1.0, klDelta = -1.0
        )
        assertEquals("[16;32) OR [32;64) OR [8;16)", r1.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(6.65,0.72),[16;32)(9.98,0.49),[8;16)(11.64,0.33)",
            r1.structure(database, function = Rule<Int>::conviction)
        )

        val r2 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::conviction, functionDelta = 2.0, klDelta = -1.0
        )
        assertEquals("[16;32) OR [32;64)", r2.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(6.65,0.60),[16;32)(9.98,0.27)",
            r2.structure(database, function = Rule<Int>::conviction)
        )

        val r10 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::conviction, functionDelta = 10.0, klDelta = -1.0
        )
        assertEquals("[32;64)", r10.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(6.65,0.00)",
            r10.structure(database, function = Rule<Int>::conviction)
        )
    }

    @Test
    fun testOptimizeLoeThresholds() {
        val predicates =
            (0..5).map { RangePredicate(2.0.pow(it.toDouble()).toInt(), 2.0.pow(it.toDouble() + 1).toInt()) }
        val database = (0..100).toList()
        val r0 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::loe, functionDelta = 0.0, klDelta = -1.0
        )
        assertEquals("[16;32) OR [1;2) OR [2;4) OR [32;64) OR [4;8) OR [8;16)", r0.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(2.66,0.79),[16;32)(2.99,0.62),[8;16)(3.11,0.50),[4;8)(3.16,0.42),[2;4)(3.19,0.38),[1;2)(3.20,0.35)",
            r0.structure(database, function = Rule<Int>::loe)
        )

        val r01 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::loe, functionDelta = 0.1, klDelta = -1.0
        )
        assertEquals("[16;32) OR [32;64) OR [8;16)", r01.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(2.66,0.72),[16;32)(2.99,0.49),[8;16)(3.11,0.33)",
            r01.structure(database, function = Rule<Int>::loe)
        )

        val r1 = optimizeWithProbes(
            RangePredicate(0, 80), database, predicates,
            function = Rule<Int>::loe, functionDelta = 1.0, klDelta = -1.0
        )
        assertEquals("[32;64)", r1.rule.conditionPredicate.name())
        assertEquals(
            "[32;64)(2.66,0.00)",
            r1.structure(database, function = Rule<Int>::loe)
        )

    }


    @Test
    fun testOptimizeOnlyKLDelta() {
        val predicates =
            (0..5).map { RangePredicate(2.0.pow(it.toDouble()).toInt(), 2.0.pow(it.toDouble() + 1).toInt()) }
        // Check that exact values can be slightly different overall optimization results persist
        listOf(100, 500, 1000, 100000).forEach { size ->
            val database = (0..size).toList()

            assertEquals(
                "[32;64)(),[16;32)(),[8;16)(),[4;8)(),[2;4)(),[1;2)()",
                optimizeWithProbes(
                    RangePredicate(0, 80), database, predicates,
                    function = Rule<Int>::conviction, functionDelta = 0.0, klDelta = FishboneMiner.KL_DELTA
                )
                    .structure(
                        database, logConviction = false, logKL = false,
                        function = Rule<Int>::conviction
                    )
            )

            // NOTE that [4;8) is placed before [16;32), this is result of high kullbackLeibler delta
            assertEquals(
                "[32;64)(),[4;8)(),[16;32)(),[8;16)()",
                optimizeWithProbes(
                    RangePredicate(0, 80), database, predicates,
                    function = Rule<Int>::conviction, functionDelta = 0.0, klDelta = 0.1
                )
                    .structure(
                        database, logConviction = false, logKL = false,
                        function = Rule<Int>::conviction
                    )
            )

            assertEquals(
                "[32;64)()",
                optimizeWithProbes(
                    RangePredicate(0, 80), database, predicates,
                    function = Rule<Int>::conviction, functionDelta = 0.0, klDelta = 0.5
                )
                    .structure(
                        database, logConviction = false, logKL = false,
                        function = Rule<Int>::conviction
                    )
            )
        }
    }

    @Test
    fun testOptimizeConviction() {
        testOptimize(Rule<Int>::conviction)
    }

    @Test
    fun testOptimizeLOE() {
        testOptimize(Rule<Int>::loe)
    }

    private fun testOptimize(function: KProperty1<Rule<Int>, Double>) {
        val predicates = predicates(10, 100)
        listOf(100, 1000, 5000).forEach { size ->
            val database = (0..size).toList()
            assertEquals(
                size.toString(), "[20;30) OR [30;40) OR [40;50)",
                optimizeWithProbes(
                    RangePredicate(20, 50),
                    database,
                    predicates,
                    function = function
                ).rule.conditionPredicate.name()
            )
            assertEquals(
                size.toString(), "[20;30) OR [30;40) OR [40;50) OR [50;60)",
                optimizeWithProbes(
                    RangePredicate(20, 60),
                    database,
                    predicates,
                    function = function
                ).rule.conditionPredicate.name()
            )
            assertEquals(
                size.toString(), "[20;30) OR [30;40) OR [40;50)",
                optimizeWithProbes(
                    RangePredicate(20, 51),
                    database,
                    predicates,
                    function = function
                ).rule.conditionPredicate.name()
            )
        }
    }


    fun testCounterExamplesConviction() {
        testCounterExamples(Rule<Int>::conviction)
    }

    fun testCounterExamplesLOE() {
        testCounterExamples(Rule<Int>::loe)
    }

    private fun testCounterExamples(function: KProperty1<Rule<Int>, Double>) {
        val target: Predicate<Int> = RangePredicate(-100, 100).named("T")
        val maxTop = 5
        val predicates = 10
        for (p in 3..predicates) {
            LOG.info("Standard score function and predicates: $p")
            val x0 = RangePredicate(-100, 1000).named("X0")
            val order = arrayListOf(x0)
            val database = IntRange(-10000, 10000).toList()
            for (i in 0 until p - 1) {
                order.add(
                    RangePredicate(-10000 + 100 * i, -10000 + predicates * 100 + 100 * i)
                        .or(RangePredicate(-120, 100))
                        .or(RangePredicate(100 + 100 * i, 200 + 100 * i)).named("X${i + 1}")
                )
            }
            order.add(RangePredicate(-120, 120).named("Z"))
            val solutionX = Predicate.and(order.subList(0, order.size - 1))
            val bestRule = Rule(solutionX, target, database)
            LOG.info("Best rule ${bestRule.name}")
            val correctOrderRule = FishboneMiner.mine(
                order, target, database,
                function = function, maxComplexity = 20
            ).first().rule
            LOG.info("Rule correct order ${correctOrderRule.name}")
            if (function(correctOrderRule) > function(bestRule)) {
                fail("Best rule is not optimal.")
            }

            LOG.time(level = Level.INFO, message = "RM") {
                for (top in 1..maxTop) {
                    val rule = FishboneMiner.mine(
                        order, target, database,
                        maxComplexity = 20,
                        topPerComplexity = maxTop,
                        function = function
                    ).first().rule
                    LOG.debug("RuleNode (top=$top): ${rule.name}")
                    assertTrue(function(rule) >= function(bestRule))
                }
            }
        }
    }


    companion object {
        internal val LOG = LoggerFactory.getLogger(RulesMinerTest::class.java)
    }
}


class RangePredicate(private val start: Int, private val end: Int) : Predicate<Int>() {

    override fun test(item: Int): Boolean = item in start until end

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
