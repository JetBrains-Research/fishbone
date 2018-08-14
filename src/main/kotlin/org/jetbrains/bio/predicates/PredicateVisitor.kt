package org.jetbrains.bio.predicates

/**
 * Basic recursive predicates visitor
 * @author Oleg Shpynov
 * @since 12/1/14
 */
open class PredicateVisitor<T> {

    open fun visitNot(predicate: NotPredicate<T>) {
        predicate.operand.accept(this)
    }

    open fun visitParenthesisPredicate(predicate: ParenthesesPredicate<T>) {
        predicate.operand.accept(this)
    }

    open fun visitAndPredicate(predicate: AndPredicate<T>) {
        predicate.operands.forEach { p -> p.accept(this) }
    }

    open fun visitOrPredicate(predicate: OrPredicate<T>) {
        predicate.operands.forEach { p -> p.accept(this) }
    }

    open fun visit(predicate: Predicate<T>) {}
}
