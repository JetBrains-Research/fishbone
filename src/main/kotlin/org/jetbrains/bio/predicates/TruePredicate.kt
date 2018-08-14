package org.jetbrains.bio.predicates

/**
 * @author Oleg Shpynov
 * @since 18.2.15
 */
class TruePredicate<T> : Predicate<T>() {
    override fun name() = "TRUE"

    override fun test(item: T): Boolean {
        return true
    }

    override fun negate(): Predicate<T> {
        return FalsePredicate()
    }

    override fun equals(other: Any?): Boolean {
        return other is TruePredicate<*>
    }
}
