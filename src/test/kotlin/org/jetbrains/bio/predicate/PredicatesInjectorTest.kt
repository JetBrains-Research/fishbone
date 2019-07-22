package org.jetbrains.bio.predicate

import junit.framework.TestCase
import org.jetbrains.bio.predicate.PredicateParser.namesFunction
import org.jetbrains.bio.rule.PredicatesInjector

class PredicatesInjectorTest : TestCase() {

    private val predicatesFunction: (String) -> Predicate<Int>? =
            namesFunction(PredicateTest.namedRangePredicates(10, 10000))

    private fun p(text: String): Predicate<Int> {
        return PredicateParser.parse(text, predicatesFunction)!!
    }

    @Throws(Exception::class)
    fun testInjectNotParenthesis() {
        val predicates = PredicatesInjector.injectPredicate(p("NOT (0 OR 1)"), p("2"))
        assertEquals("""2 AND NOT (0 OR 1)
2 OR NOT (0 OR 1)""",
                ts(predicates))
    }


    fun testInjectNotOr() {
        val predicates = PredicatesInjector.injectPredicate(p("0 OR NOT 1"), p("2"))
        assertEquals("""(0 OR NOT 1) AND 2
0 AND 2 OR NOT 1
0 OR 2 AND NOT 1
0 OR 2 OR NOT 1""",
                ts(predicates))
    }


    @Throws(Exception::class)
    fun testInjectAndPredicate() {
        val predicates = PredicatesInjector.injectPredicate(p("0 AND 1"), p("2"))
        assertEquals("""(0 OR 2) AND 1
(1 OR 2) AND 0
0 AND 1 AND 2
0 AND 1 OR 2""",
                ts(predicates))
    }

    @Throws(Exception::class)
    fun testInjectSinglePredicate() {
        val predicates = PredicatesInjector.injectPredicate(p("0"), p("1"))
        assertEquals("""0 AND 1
0 OR 1""",
                ts(predicates))
    }

    @Throws(Exception::class)
    fun testInjectNotPredicate() {
        val predicates = PredicatesInjector.injectPredicate(p("NOT 0"), p("1"))
        assertEquals("""1 AND NOT 0
1 OR NOT 0""",
                ts(predicates))
    }

    @Throws(Exception::class)
    fun testInjectParenthesisPredicate() {
        val predicates = PredicatesInjector.injectPredicate(p("(0)"), p("1"))
        assertEquals("""0 AND 1
0 OR 1""", ts(predicates))
    }

    @Throws(Exception::class)
    fun testAndOnly() {
        val predicates = PredicatesInjector.injectPredicate(p("(0)"), p("1"), and = true, or = false)
        assertEquals("0 AND 1", ts(predicates))
    }

    @Throws(Exception::class)
    fun testOrOnly() {
        val predicates = PredicatesInjector.injectPredicate(p("(0)"), p("1"), and = false, or = true)
        assertEquals("0 OR 1", ts(predicates))
    }

    @Throws(Exception::class)
    fun testNothing() {
        val predicates = PredicatesInjector.injectPredicate(p("(0)"), p("1"), and = false, or = false)
        assertEquals("", ts(predicates))
    }


    private fun ts(predicates: Collection<Predicate<Int>>): String =
            predicates.map { it.name() }.sorted().joinToString("\n")
}