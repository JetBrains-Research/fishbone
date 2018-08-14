package org.jetbrains.bio.predicates

import java.util.*

/**
 * Invariant: predicate is defined and can be negated.
 * @author Oleg Shpynov
 * @since 20.11.14
 */
class NotPredicate<T>(val operand: Predicate<T>) : Predicate<T>() {
    override fun name(): String {
        return "NOT ${operand.name()}"
    }


    override fun negate(): Predicate<T> {
        return (operand as? ParenthesesPredicate<T>)?.operand ?: operand
    }

    override fun test(item: T): Boolean {
        return !operand.test(item)
    }

    override fun testUncached(items: List<T>): BitSet {
        val result = operand.test(items).clone() as BitSet
        result.flip(0, items.size)
        return result
    }

    override fun accept(visitor: PredicateVisitor<T>) {
        visitor.visitNot(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotPredicate<*>) return false
        return operand == other.operand
    }

    override fun hashCode(): Int {
        return Objects.hash(operand)
    }

    companion object {

        /**
         * Use [Predicate.negate] in general case, because it can have specific implementation,
         * i.e. CODING_GENE.negate -> NON_CODING_GENE and vise versa
         * @return NotPredicate in case when predicate is defined,
         * * P if predicate is NOT(P)
         * * Undefined in case predicate is undefined
         */
        fun <T> of(predicate: Predicate<T>): Predicate<T> {
            if (predicate is NotPredicate<*>) {
                return (predicate as NotPredicate<T>).operand
            }

            return if (predicate.canNegate())
                NotPredicate(predicate)
            else
                UndefinedPredicate()
        }
    }
}
