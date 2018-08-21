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

    init {
        check(operands.size >= 2) {
            "Expected at least 2 operands"
        }
    }

    override fun canNegate(): Boolean {
        return operands.all { it.canNegate() }
    }

    override fun negate(): Predicate<T> {
        return NotPredicate.of(ParenthesesPredicate.of(this))
    }

    override fun test(item: T): Boolean {
        return operands.all { it.test(item) }
    }

    override fun name(): String = operands.joinToString(" ${PredicateParser.AND} ") { it.name() }

    fun length(): Int {
        return operands.size
    }

    fun remove(index: Int): Predicate<T> {
        val operands = Lists.newArrayList(operands)
        operands.removeAt(index)
        return and(operands)
    }

    override fun testUncached(items: List<T>): BitSet {
        val trueBS = BitSet(items.size)
        trueBS.flip(0, items.size)
        return operands.fold(trueBS) { bitSet, predicate -> bitSet.and(predicate.test(items)); bitSet }
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
}
