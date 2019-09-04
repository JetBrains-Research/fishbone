package org.jetbrains.bio.fishbone.predicate

import com.google.common.collect.Lists
import java.util.*

/**
 * Invariant: all operands are defined.
 * @author Oleg Shpynov
 * @since 20/11/14
 */
class OrPredicate<T>(val operands: List<Predicate<T>>) : Predicate<T>() {

    init {
        check(operands.size >= 2) {
            "Expected at least 2 operands"
        }
    }

    override fun not(): Predicate<T> {
        return NotPredicate.of(ParenthesesPredicate.of(this))
    }

    override fun test(item: T): Boolean {
        return operands.any { it.test(item) }
    }

    override fun name(): String = operands.joinToString(" ${PredicateParser.OR} ") { it.name() }

    fun length(): Int {
        return operands.size
    }

    fun remove(index: Int): Predicate<T> {
        val operands = Lists.newArrayList(operands)
        operands.removeAt(index)
        return or(operands)
    }

    override fun testUncached(items: List<T>): BitSet {
        return operands.fold(BitSet()) { bitSet, predicate -> bitSet.or(predicate.test(items)); bitSet }
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

}
