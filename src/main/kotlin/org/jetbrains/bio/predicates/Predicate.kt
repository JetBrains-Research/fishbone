package org.jetbrains.bio.predicates

import com.google.common.base.Preconditions.checkState
import com.google.common.collect.Sets
import java.lang.ref.WeakReference
import java.util.*

/**
 * Predicate, used in predicates mining.
 * NOTE It doesn't extend interface [java.util.function.Predicate] because of clashes in methods #and, #or, etc

 * @author Oleg Shpynov
 * @since 17/11/14
 */
abstract class Predicate<T> {

    abstract fun test(item: T): Boolean

    abstract fun name(): String

    /**
     * Predicate is defined, in case when all the subpredicates are defined.
     * The only atomic undefined predicate considered to be [UndefinedPredicate]
     */
    open fun defined(): Boolean = true

    /**
     * Used in [NotPredicate] creation, returns NotPredicate if true,
     * and [UndefinedPredicate] otherwise
     */
    open fun canNegate(): Boolean = true

    /**
     * Return negotiation of predicate.
     * Important: can return [UndefinedPredicate], use [.canNegate] to check
     */
    open fun not(): Predicate<T> {
        checkState(canNegate(), "cannot negate")
        return NotPredicate.of(this)
    }

    fun and(other: Predicate<T>): Predicate<T> = and(this, other)

    fun or(other: Predicate<T>): Predicate<T> = or(this, other)

    @Volatile
    private var cachedDataBase: List<T>? = null
    @Volatile
    private var cache = WeakReference<BitSet>(null)

    /**
     * Please use [testUncached] to implement custom behavior.
     * NOTE: we don't use Cache here, because items is the same object, so that cache miss should be quite rare.
     */
    @Synchronized
    open fun test(items: List<T>): BitSet {
        // NOTE: We use reference equality check instead of Lists equality because it can be slow on large databases.
        var result = if (cachedDataBase !== items) {
            null
        } else {
            cache.get()
        }
        if (result == null) {
            cachedDataBase = items
            result = testUncached(items)
            cache = WeakReference(result)
        }
        return result
    }

    protected open fun testUncached(items: List<T>): BitSet {
        val result = BitSet(items.size)
        for (i in items.indices) {
            result.set(i, test(items[i]))
        }
        return result
    }

    open fun accept(visitor: PredicateVisitor<T>) {
        visitor.visit(this)
    }

    fun collectAtomics(): Set<Predicate<T>> {
        val atomics = Sets.newHashSet<Predicate<T>>()
        accept(object : PredicateVisitor<T>() {
            override fun visit(predicate: Predicate<T>) {
                atomics.add(predicate)
            }
        })
        return atomics
    }

    fun complexity(): Int = collectAtomics().size

    override fun toString(): String {
        return name()
    }

    companion object {

        /**
         * Returns [OrPredicate] if all operands are defined and the
         * number of operands >= 2. If there's only one defined operand,
         * returns it unchanged. Otherwise returns [UndefinedPredicate].
         */
        fun <T> or(operands: List<Predicate<T>>): Predicate<T> {
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
                    .sortedBy { it.name() }
            if (processedOperands.any { it == TruePredicate<T>() }) {
                return TruePredicate()
            }
            return if (processedOperands.size == 1) processedOperands[0] else OrPredicate(processedOperands)
        }


        @SafeVarargs
        fun <T> or(vararg operands: Predicate<T>): Predicate<T> {
            return or(Arrays.asList(*operands))
        }

        /**
         * Returns [AndPredicate] if all operands are defined and the
         * number of operands >= 2. If there's only one defined operand,
         * returns it unchanged. Otherwise returns [UndefinedPredicate].
         */
        fun <T> and(operands: List<Predicate<T>>): Predicate<T> {
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
                    .sortedBy { it.name() }
            // Check FALSE inside operands
            if (processedOperands.any { it == FalsePredicate<T>() }) {
                return FalsePredicate()
            }
            return if (processedOperands.size == 1) processedOperands[0] else AndPredicate(processedOperands)
        }

        @SafeVarargs
        fun <T> and(vararg operands: Predicate<T>): Predicate<T> {
            return and(Arrays.asList(*operands))
        }
    }
}
