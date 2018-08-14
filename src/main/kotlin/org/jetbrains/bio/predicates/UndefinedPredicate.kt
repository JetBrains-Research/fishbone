package org.jetbrains.bio.predicates

/**
 * Predicate to mark undefined behavior in case logical system is Constructivism
 * http://en.wikipedia.org/wiki/Constructivism_(mathematics)
 *
 * I.E. A or not A is not TRUE

 * @author Oleg Shpynov
 * @since 27.4.15
 */
class UndefinedPredicate<T> : Predicate<T>() {
    override fun name(): String {
        return "undefined"
    }

    override fun defined() = false

    override fun test(item: T): Boolean {
        throw IllegalStateException("#result is undefined")
    }

    override fun canNegate(): Boolean {
        return false
    }

    override fun negate(): Predicate<T> {
        return this
    }

    override fun equals(other: Any?): Boolean {
        return other is UndefinedPredicate<*>
    }
}
