package org.jetbrains.bio.fishbone.predicate

import com.google.common.base.Preconditions.checkState
import java.util.*

/**
 * @author Oleg Shpynov
 * @since 16.12.14
 */
class ParenthesesPredicate<T> internal constructor(val operand: Predicate<T>) : Predicate<T>() {
    override fun name(): String {
        return "${PredicateParser.LPAR.token}${operand.name()}${PredicateParser.RPAR.token}"
    }

    override fun canNegate(): Boolean {
        return operand.canNegate()
    }

    override fun not(): Predicate<T> {
        checkState(canNegate(), "cannot negate")
        return of(operand.not())
    }

    override fun test(item: T): Boolean {
        return operand.test(item)
    }

    override fun testUncached(items: List<T>): BitSet {
        return operand.test(items)
    }

    override fun accept(visitor: PredicateVisitor<T>) {
        visitor.visitParenthesisPredicate(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParenthesesPredicate<*>) return false
        return operand == other.operand
    }

    override fun hashCode(): Int {
        return Objects.hash(operand)
    }

    companion object {

        fun <T> of(predicate: Predicate<T>): Predicate<T> {
            if (!predicate.defined()) {
                return UndefinedPredicate()
            }
            // Optimize complexity
            return if (predicate is AndPredicate<*> || predicate is OrPredicate<*>)
                ParenthesesPredicate(predicate)
            else predicate
        }
    }
}
