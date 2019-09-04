package org.jetbrains.bio.fishbone.predicate

/**
 * @author Oleg Shpynov
 * @since 18.2.15
 */
class FalsePredicate<T> : Predicate<T>() {
    override fun name() = PredicateParser.FALSE.token

    override fun test(item: T): Boolean {
        return false
    }

    override fun not(): Predicate<T> {
        return TruePredicate()
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is FalsePredicate<*>
    }
}
