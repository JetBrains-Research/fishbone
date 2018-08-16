package org.jetbrains.bio.predicates

import com.google.common.base.Preconditions.checkState
import com.google.common.collect.Sets
import java.lang.ref.WeakReference
import java.util.*
import java.util.function.Function

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
    open fun negate(): Predicate<T> {
        checkState(canNegate(), "cannot negate")
        return NotPredicate.of(this)
    }

    fun and(other: Predicate<T>): Predicate<T> = AndPredicate.of(this, other)

    fun or(other: Predicate<T>): Predicate<T> = OrPredicate.of(this, other)

    @Volatile private var cachedDataBase: List<T>? = null
    @Volatile private var cache = WeakReference<BitSet>(null)

    /**
     * Please use [.testUncached] to implement custom behavior.
     * NOTE: we don't use Cache here, because items is the same object, so that cache miss should be quite rare.
     */
    @Synchronized
    open fun test(items: List<T>): BitSet {
        var result: BitSet?
        if (cachedDataBase !== items) {
            result = null
        } else {
            result = cache.get()
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
        val NAMES_COMPARATOR: Comparator<Predicate<*>> =
                Comparator.comparing(Function<Predicate<*>, String> { it.name() })
    }
}
