package org.jetbrains.bio.fishbone.predicate

import org.jetbrains.bio.util.Lexeme
import org.jetbrains.bio.util.Match
import org.jetbrains.bio.util.Tokenizer

/**
 * @author Oleg Shpynov
 * @since 30.1.15
 */
object PredicateParser {

    /**
     * NOTE we operate with small predicates, so that we don't really need Finite Automate based lexer
     */
    val IMPL = Lexeme("=>")

    internal val NOT = Lexeme("NOT")
    internal val AND = Lexeme("AND")
    internal val OR = Lexeme("OR")
    internal val LPAR = Lexeme("(")
    internal val RPAR = Lexeme(")")
    internal val TRUE = Lexeme("TRUE")
    internal val FALSE = Lexeme("FALSE")

    private val KEYWORDS: Set<Lexeme> = setOf(NOT, AND, OR, LPAR, RPAR, IMPL, TRUE, FALSE)

    fun <T> parse(text: String, factory: (String) -> Predicate<T>?): Predicate<T>? {
        return parse(Tokenizer(text, KEYWORDS), factory)
    }

    fun <T> parse(tokenizer: Tokenizer, factory: (String) -> Predicate<T>?): Predicate<T>? {
        return parseOr(tokenizer, factory)
    }

    private fun <T> parseOr(tokenizer: Tokenizer, factory: (String) -> Predicate<T>?): Predicate<T>? {
        val operand1 = parseAnd(tokenizer, factory)
        checkNotNull(operand1) { error(tokenizer) }


        var lexeme: Lexeme? = tokenizer.fetch()
        if (lexeme !== OR) {
            return operand1
        }
        val operands = arrayListOf(operand1)
        while (lexeme === OR) {
            tokenizer.next()
            val nextOperand = parseAnd(tokenizer, factory)
            checkNotNull(nextOperand) { error(tokenizer) }
            operands.add(nextOperand)
            lexeme = tokenizer.fetch()
        }

        return OrPredicate(operands)
    }

    private fun <T> parseAnd(tokenizer: Tokenizer, factory: (String) -> Predicate<T>?): Predicate<T>? {
        val operand1 = parseTerm(tokenizer, factory)
        checkNotNull(operand1) { error(tokenizer) }

        var lexeme: Lexeme? = tokenizer.fetch()
        if (lexeme !== AND) {
            return operand1
        }
        val operands = arrayListOf(operand1)
        while (lexeme === AND) {
            tokenizer.next()
            val nextOperand = parseTerm(tokenizer, factory)
            checkNotNull(nextOperand) { error(tokenizer) }
            operands.add(nextOperand)
            lexeme = tokenizer.fetch()
        }

        return AndPredicate(operands)
    }

    private fun <T> parseTerm(tokenizer: Tokenizer, factory: (String) -> Predicate<T>?): Predicate<T>? {
        val lexeme = tokenizer.fetch() ?: return null
        if (lexeme === TRUE) {
            tokenizer.next()
            return TruePredicate()
        }
        if (lexeme === FALSE) {
            tokenizer.next()
            return FalsePredicate()
        }
        if (lexeme === LPAR) {
            tokenizer.next()
            val p = parse(tokenizer, factory)
            tokenizer.check(RPAR)
            checkNotNull(p) { error(tokenizer) }
            // Use direct constructor, because of method can perform complexity transformations
            return ParenthesesPredicate(p)
        }
        if (lexeme === NOT) {
            tokenizer.next()
            val p = parseTerm(tokenizer, factory)
            checkNotNull(p) { error(tokenizer) }
            return NotPredicate(p)
        }

        if (!KEYWORDS.contains(lexeme)) {
            // Lookahead
            val initMatch = tokenizer.match
            var match = initMatch
            while (match != null) {
                val lookahead = tokenizer.text.substring(initMatch!!.start, match.end).trim { it <= ' ' }
                val predicate = factory(lookahead)
                if (predicate != null) {
                    tokenizer.lookahead(Match(Lexeme(lookahead), initMatch.start, match.end))
                    tokenizer.next()
                    return predicate
                }
                tokenizer.next()
                tokenizer.fetch()
                match = tokenizer.match
            }
            tokenizer.lookahead(initMatch!!)
        }
        throw IllegalStateException(error(tokenizer))
    }

    private fun error(tokenizer: Tokenizer): String {
        return "Failed to parse predicate: $tokenizer"
    }

    /**
     * Transforms collection of predicates to map for parsing.
     * NOTE it removes all the spaces, which is important for parsing
     */
    fun <T> namesFunction(predicates: Collection<Predicate<T>>): (String) -> Predicate<T>? {
        val map = predicates.associateBy { it.name() }
        return { map[it] }
    }

}
