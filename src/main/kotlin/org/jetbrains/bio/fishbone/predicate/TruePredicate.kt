package org.jetbrains.bio.fishbone.predicate

/**
 * @author Oleg Shpynov
 * @since 18.2.15
 */
class TruePredicate<T> : Predicate<T>() {
    override fun name() = PredicateParser.TRUE.token

    override fun test(item: T): Boolean {
        return true
    }

    override fun not(): Predicate<T> {
        return FalsePredicate()
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is TruePredicate<*>
    }
}
