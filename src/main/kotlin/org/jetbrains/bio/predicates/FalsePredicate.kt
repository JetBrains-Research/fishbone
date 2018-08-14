package org.jetbrains.bio.predicates

/**
 * @author Oleg Shpynov
 * @since 18.2.15
 */
class FalsePredicate<T> : Predicate<T>() {
    override fun name() = "FALSE"

    override fun test(item: T): Boolean {
        return false
    }

    override fun negate(): Predicate<T> {
        return TruePredicate()
    }

    override fun equals(other: Any?): Boolean {
        return other is FalsePredicate<*>
    }
}
