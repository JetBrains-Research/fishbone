package org.jetbrains.bio.fishbone.predicate

import junit.framework.TestCase

class PredicateTest : TestCase() {

    private val predicates: List<Predicate<Int>> =
            namedRangePredicates(10, 10000) +
                    listOf(TruePredicate(), FalsePredicate())


    @Throws(Exception::class)
    fun testCollectAtomicFormulas() {
        val predicates = namedRangePredicates(4, 10000)
        val set = ParenthesesPredicate.of(Predicate.or(predicates[0].not(),
                Predicate.and(predicates[1], predicates[2]))).collectAtomics()
        assertTrue(set.contains(predicates[0]))
        assertTrue(set.contains(predicates[1]))
        assertTrue(set.contains(predicates[2]))
        assertFalse(set.contains(predicates[3]))
    }

    private fun p(text: String): Predicate<Int> {
        return PredicateParser.parse(text, PredicateParser.namesFunction(predicates))!!
    }

    @Throws(Exception::class)
    fun testOr() {
        assertEquals("0 OR 1", p("0").or(p("1")).toString())
        assertEquals("0 OR 1 OR 2", p("0 OR 1").or(p("2")).toString())
        assertEquals("0 OR 1 OR 2", p("1").or(p("0 OR 2")).toString())
        assertEquals("0 OR 1 OR 2 OR 3", p("0 OR 2").or(p("1 OR 3")).toString())
        assertEquals("0 OR 1 OR 2 AND 3", p("0 OR 1").or(p("2 AND 3")).toString())
        assertEquals("0 OR 1 OR 2", p("0 OR 1").or(p("(2)")).toString())
        assertEquals(p("1"), Predicate.or(p("1")))
        assertEquals(p("0"), Predicate.or(p("FALSE"), p("0")))
        assertEquals(p("TRUE"), Predicate.or(p("TRUE"), p("0")))
    }

    @Throws(Exception::class)
    fun testAnd() {
        assertEquals("0 AND 1", p("0").and(p("1")).toString())
        assertEquals("0 AND 1 AND 2", p("0 AND 1").and(p("2")).toString())
        assertEquals("0 AND 1 AND 2", p("1").and(p("0 AND 2")).toString())
        assertEquals("0 AND 1 AND 2 AND 3", p("0 AND 2").and(p("1 AND 3")).toString())
        assertEquals("(2 OR 3) AND 0 AND 1", p("0 AND 1").and(p("2 OR 3")).toString())
        assertEquals(p("1"), Predicate.and(p("1")))
        assertEquals(p("0"), Predicate.and(p("TRUE"), p("0")))
        assertEquals(p("FALSE"), Predicate.and(p("FALSE"), p("0")))
    }

    @Throws(Exception::class)
    fun testRemoveOr() {
        assertEquals("0 OR 2", (p("0 OR 1 OR 2") as OrPredicate<*>).remove(1).toString())
    }

    @Throws(Exception::class)
    fun testRemoveOrSingle() {
        assertEquals("0", (p("0 OR 1") as OrPredicate<*>).remove(1).name())
    }

    @Throws(Exception::class)
    fun testRemoveAnd() {
        assertEquals("0 AND 2", (p("0 AND 1 AND 2") as AndPredicate<*>).remove(1).toString())
    }

    @Throws(Exception::class)
    fun testRemoveAndSingle() {
        assertEquals("0", (p("0 AND 1") as AndPredicate<*>).remove(1).toString())
    }

    @Throws(Exception::class)
    fun testNotNegate() {
        assertEquals("0", predicates[0].not().not().name())
        assertFalse(UndefinedPredicate<Any>().not().defined())
    }

    @Throws(Exception::class)
    fun testParentsNegate() {
        assertEquals("0", p("(NOT 0)").not().name())
        assertEquals("0", p("NOT (0)").not().name())
        assertFalse(ParenthesesPredicate.of(UndefinedPredicate<Any>()).not().defined())
    }


    @Throws(Exception::class)
    fun testOrNegate() {
        assertEquals("NOT (0 OR 1 OR 2)", p("0 OR 1 OR 2").not().name())
        assertFalse(Predicate.or(p("0"), UndefinedPredicate<Int>()).not().defined())
    }

    @Throws(Exception::class)
    fun testAndNegate() {
        assertEquals("NOT (0 AND 1 AND 2)", p("0 AND 1 AND 2").not().name())
        assertFalse(Predicate.and(p("0"), UndefinedPredicate<Int>()).not().defined())
    }

    @Throws(Exception::class)
    fun testGetNameOrder() {
        val predicates = namedRangePredicates(4, 10000)
        assertEquals("0 OR 1 OR 2", Predicate.or(predicates[1], predicates[0], predicates[2]).name())
        assertEquals("0 AND 1 AND 2", Predicate.and(predicates[1], predicates[0], predicates[2]).name())
    }

    fun testEquals() {
        val predicates = namedRangePredicates(3, 10000)

        assertEquals(TruePredicate<Any>(), TruePredicate<Any>())
        assertEquals(FalsePredicate<Any>(), FalsePredicate<Any>())
        assertEquals(UndefinedPredicate<Any>(), UndefinedPredicate<Any>())

        assertEquals(TruePredicate<Any>(), FalsePredicate<Any>().not())
        assertEquals(FalsePredicate<Any>(), TruePredicate<Any>().not())
        assertEquals(UndefinedPredicate<Any>(), UndefinedPredicate<Any>().not())


        assertEquals(Predicate.or(predicates[0], predicates[1], predicates[2]),
                Predicate.or(predicates[0], predicates[1], predicates[2]))
        // Check operands get sorted
        assertEquals(Predicate.or(predicates[0], predicates[1], predicates[2]),
                Predicate.or(predicates[1], predicates[2], predicates[0]))
        assertNotSame(Predicate.or(predicates[0], predicates[1]),
                Predicate.or(predicates[0], predicates[1], predicates[2]))

        assertEquals(Predicate.and(predicates[0], predicates[1], predicates[2]),
                Predicate.and(predicates[0], predicates[1], predicates[2]))
        // Check operands get sorted
        assertEquals(Predicate.and(predicates[0], predicates[1], predicates[2]),
                Predicate.and(predicates[1], predicates[0], predicates[2]))
        assertNotSame(Predicate.and(predicates[0], predicates[1]),
                Predicate.and(predicates[0], predicates[1], predicates[2]))

        assertEquals(predicates[0].not(), predicates[0].not())
        assertEquals(ParenthesesPredicate.of(predicates[0]), ParenthesesPredicate.of(predicates[0]))
    }


    fun testUndefined() {
        val predicates = namedRangePredicates(4, 10000)
        assertFalse(UndefinedPredicate<Any>().defined())
        assertFalse(Predicate.or(predicates[1],
                Predicate.and(predicates[0], UndefinedPredicate<Int>())).defined())
    }


    @Throws(Exception::class)
    fun testComplexity() {
        assertEquals(1, p("0").complexity())
        assertEquals(1, p("NOT 0").complexity())
        assertEquals(3, p("0 OR 1 OR 2").complexity())
        assertEquals(3, p("0 AND 1 AND 2").complexity())
        assertEquals(3, p("0 AND 1 OR 2").complexity())
        assertEquals(3, p("0 AND (1 OR 2)").complexity())
    }

    companion object {

        fun namedRangePredicates(n: Int, database: Int): List<Predicate<Int>> {
            return (0.until(n)).map { i ->
                object : Predicate<Int>() {
                    override fun test(item: Int): Boolean {
                        return database * i / n <= item && item < database * (i + 1) / n
                    }

                    override fun name(): String {
                        return i.toString()
                    }
                }
            }
        }
    }
}