package org.jetbrains.bio.predicates

import com.google.common.collect.Lists
import java.util.*

/**
 * Invariant: all operands are defined.
 * @author Oleg Shpynov
 * @since 20/11/14
 */
class OrPredicate<T>(val operands: List<Predicate<T>>) : Predicate<T>() {
    override fun negate(): Predicate<T> {
        return NotPredicate.of(ParenthesesPredicate.of(this))
    }

    override fun test(item: T): Boolean {
        return operands.any { it.test(item) }
    }

    override fun name(): String = operands.map { it.name() }.joinToString(" ${PredicateParser.OR} ")

    fun length(): Int {
        return operands.size
    }

    fun remove(index: Int): Predicate<T> {
        val operands = Lists.newArrayList(operands)
        operands.removeAt(index)
        return of(operands)
    }

    override fun testUncached(items: List<T>): BitSet {
        return operands.fold(BitSet(), { bitSet, predicate -> bitSet.or(predicate.test(items)); bitSet })
    }

    override fun accept(visitor: PredicateVisitor<T>) {
        visitor.visitOrPredicate(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrPredicate<*>) return false
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
                    // Remove unnecessary ()
                    .map { o -> if (o is ParenthesesPredicate<*>) (o as ParenthesesPredicate<T>).operand else o }
                    // Open underlying Or operands
                    .flatMap { o -> if (o is OrPredicate<*>) (o as OrPredicate<T>).operands else listOf(o) }
                    // Filter FALSE operands
                    .filter { o -> o != FalsePredicate<T>() }
                    .sortedWith(NAMES_COMPARATOR)
            if (processedOperands.any { it == TruePredicate<T>() }) {
                return TruePredicate()
            }
            return if (processedOperands.size == 1) processedOperands[0] else OrPredicate(processedOperands)
        }

        /**
         * Returns [OrPredicate] if all operands are defined and the
         * number of operands >= 2. If there's only one defined operand,
         * returns it unchanged. Otherwise returns [UndefinedPredicate].
         */
        @SafeVarargs
        fun <T> of(vararg operands: Predicate<T>): Predicate<T> {
            return of(Arrays.asList(*operands))
        }
    }
}
