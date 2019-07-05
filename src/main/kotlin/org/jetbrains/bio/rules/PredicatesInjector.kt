package org.jetbrains.bio.rules

import org.jetbrains.bio.predicates.*

/**
 * Inserts predicates inside given predicate at all possible places
 */
object PredicatesInjector {

    fun <T> injectPredicate(predicate: Predicate<T>, predicate2Inject: Predicate<T>,
                            and: Boolean = true,
                            or: Boolean = true): Set<Predicate<T>> {
        val result = hashSetOf<Predicate<T>>()
        predicate.accept(createInjector(setOf(predicate2Inject), and, or) { result.add(it) })
        return result
    }

    private fun <T> createInjector(atomics: Collection<Predicate<T>>,
                                   and: Boolean = true,
                                   or: Boolean = true,
                                   consumer: (Predicate<T>) -> Unit): PredicateVisitor<T> {
        return object : PredicateVisitor<T>() {
            override fun visitAndPredicate(predicate: AndPredicate<T>) {
                process(predicate)
                predicate.operands.forEachIndexed { i, operand ->
                    // Replace operand
                    val woIndex = predicate.remove(i)
                    operand.accept(
                            createInjector(atomics, and, or) { newPredicate -> consumer(woIndex.and(newPredicate)) })
                }
            }

            override fun visitOrPredicate(predicate: OrPredicate<T>) {
                process(predicate)
                predicate.operands.forEachIndexed { i, operand ->
                    // Replace operand
                    val woIndex = predicate.remove(i)
                    operand.accept(
                            createInjector(atomics, and, or) { newPredicate -> consumer(woIndex.or(newPredicate)) })
                }
            }

            // No injection inside NOT
            override fun visitNot(predicate: NotPredicate<T>) {
                process(predicate)
            }

            override fun visitParenthesisPredicate(predicate: ParenthesesPredicate<T>) {
                process(predicate)
                predicate.operand.accept(createInjector(atomics, and, or) { consumer(it) })
            }

            override fun visit(predicate: Predicate<T>) {
                process(predicate)
            }

            private fun process(predicate: Predicate<T>) {
                atomics.forEach {
                    if (or) {
                        consumer(predicate.or(it))
                    }
                    if (and) {
                        consumer(predicate.and(it))
                    }
                }
            }
        }
    }
}