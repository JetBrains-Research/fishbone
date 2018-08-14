package org.jetbrains.bio.predicates

import com.google.common.collect.Lists
import java.util.*

/**
 * Invariant: all operands are defined.
 *
 * @author Oleg Shpynov
 * @since 20/11/14
 */
class AndPredicate<T>(val operands: List<Predicate<T>>) : Predicate<T>() {


    override fun canNegate(): Boolean {
        return operands.all { it.canNegate() }
    }

    override fun negate(): Predicate<T> {
        return NotPredicate.of(ParenthesesPredicate.of(this))
    }

    override fun test(item: T): Boolean {
        return operands.all { it.test(item) }
    }

    override fun name(): String = operands.map { it.name() }.joinToString(" ${PredicateParser.AND} ")

    fun length(): Int {
        return operands.size
    }

    fun remove(index: Int): Predicate<T> {
        val operands = Lists.newArrayList(operands)
        operands.removeAt(index)
        return of(operands)
    }

    override fun testUncached(items: List<T>): BitSet {
        val trueBS = BitSet(items.size)
        trueBS.flip(0, items.size)
        return operands.fold(trueBS, { bitSet, predicate -> bitSet.and(predicate.test(items)); bitSet })
    }

    override fun accept(visitor: PredicateVisitor<T>) {
        visitor.visitAndPredicate(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AndPredicate<*>) return false
        return operands == other.operands
    }

    override fun hashCode(): Int {
        return Objects.hashCode(operands)
    }


    companion object {

        fun <T> of(operands: List<Predicate<T>>): Predicate<T> {
            check(operands.isNotEmpty())
            if (!operands.all { it.defined() }) {
                return UndefinedPredicate()
            }
            val processedOperands = operands
                    // Insert parenthesis within Or operands
                    .map { o -> if (o is OrPredicate<*>) ParenthesesPredicate.of(o) else o }
                    // Remove unnecessary ()
                    .map { o ->
                        if (o is ParenthesesPredicate<*> && o.operand !is OrPredicate<*>)
                            (o as ParenthesesPredicate<T>).operand
                        else
                            o
                    }
                    // Open underlying And predicates
                    .flatMap { o -> if (o is AndPredicate<*>) (o as AndPredicate<T>).operands else listOf(o) }
                    // Filter TRUE operands
                    .filter { o -> o != TruePredicate<T>() }
                    .sortedWith(NAMES_COMPARATOR)
            // Check FALSE inside operands
            if (processedOperands.any { it == FalsePredicate<T>() }) {
                return FalsePredicate()
            }
            return if (processedOperands.size == 1) processedOperands[0] else AndPredicate(processedOperands)
        }

        /**
         * Returns [AndPredicate] if all operands are defined and the
         * number of operands >= 2. If there's only one defined operand,
         * returns it unchanged. Otherwise returns [UndefinedPredicate].
         */
        @SafeVarargs
        fun <T> of(vararg operands: Predicate<T>): Predicate<T> {
            return of(Arrays.asList(*operands))
        }
    }
}
