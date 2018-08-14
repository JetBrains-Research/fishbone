package org.jetbrains.bio.predicates

import com.google.common.collect.ImmutableList
import junit.framework.TestCase
import org.jetbrains.bio.predicates.PredicateParser.namesFunction

class PredicateParserTest : TestCase() {

    private fun testPredicate(name: String): Predicate<Any> {
        return object : Predicate<Any>() {
            override fun name() = name

            override fun test(item: Any): Boolean {
                throw UnsupportedOperationException("#test")
            }

        }
    }

    fun testNamesFunction() {
        assertEquals("PREDICATE",
                PredicateParser.parse("PREDICATE",
                        namesFunction(ImmutableList.of(testPredicate("PREDICATE"))))!!.name())
    }

    fun testLookahead() {
        assertEquals("[0;20)",
                PredicateParser.parse("[0;20)",
                        namesFunction(ImmutableList.of(testPredicate("[0;20)"))))!!.name())
    }

    @Throws(Exception::class)
    fun testParseNotRule() {
        assertEquals("NOT p1", PredicateParser.parse<Any>("NOT p1", { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseParenthesisRule() {
        assertEquals("(p1)", PredicateParser.parse<Any>("(p1)", { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseRuleAnd() {
        assertEquals("p1 AND p2", PredicateParser.parse<Any>("p1 AND p2", { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseRuleAnd3() {
        assertEquals("p1 AND p2 AND NOT p3",
                PredicateParser.parse<Any>("p1 AND p2 AND NOT p3", { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseRuleOr() {
        assertEquals("p1 OR p2", PredicateParser.parse<Any>("p1 OR p2", { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseRuleOr3() {
        assertEquals("p1 OR p2 OR NOT p3",
                PredicateParser.parse<Any>("p1 OR p2 OR NOT p3", { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseComplex() {
        assertEquals("NOT ICP AND NOT LCP AND NOT exons_except_first_H3K36me3",
                PredicateParser.parse<Any>("NOT ICP AND NOT LCP AND NOT exons_except_first_H3K36me3",
                        { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseComplexOr() {
        assertEquals("Insulator AND exons_except_first_H3K36me3",
                PredicateParser.parse<Any>("Insulator AND exons_except_first_H3K36me3", { testPredicate(it) })!!.name())
    }

    @Throws(Exception::class)
    fun testParseTrueFalse() {
        assertTrue(PredicateParser.parse<Any>("TRUE", { null }) is TruePredicate<*>)
        assertTrue(PredicateParser.parse<Any>("FALSE", { null }) is FalsePredicate<*>)
    }

    @Throws(Exception::class)
    fun testParseNoPredicate() {
        assertEquals("methylation-",
                PredicateParser.parse("methylation-",
                        namesFunction(ImmutableList.of(testPredicate("methylation-"))))!!.name())
    }


}