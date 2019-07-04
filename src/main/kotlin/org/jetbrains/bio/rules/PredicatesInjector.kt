package org.jetbrains.bio.rules

import com.google.common.collect.Sets
import org.jetbrains.bio.predicates.*
import java.util.function.Consumer

/**
 * Inserts predicates inside given predicate at all possible places
 */
object PredicatesInjector {

    fun <T> injectPredicate(predicate: Predicate<T>, predicate2Inject: Predicate<T>,
                            and: Boolean = true,
                            or: Boolean = true): Collection<Predicate<T>> {
        val result = Sets.newHashSet<Predicate<T>>()
        predicate.accept(createInjector(setOf(predicate2Inject), and, or, Consumer { result.add(it) }))
        return result
    }

    private fun <T> createInjector(atomics: Collection<Predicate<T>>,
                                   and: Boolean = true,
                                   or: Boolean = true,
                                   consumer: Consumer<Predicate<T>>): PredicateVisitor<T> {
        return object : PredicateVisitor<T>() {
            override fun visitAndPredicate(predicate: AndPredicate<T>) {
                process(predicate)
                val operands = predicate.operands
                for (i in 0.until(operands.size)) {
                    // Replace operand
                    val woIndex = predicate.remove(i)
                    operands[i].accept(
                            createInjector(atomics, and, or,
                                    Consumer { newPredicate -> consumer.accept(woIndex.and(newPredicate)) }))
                }
            }

            override fun visitOrPredicate(predicate: OrPredicate<T>) {
                process(predicate)
                val operands = predicate.operands
                for (i in 0.until(operands.size)) {
                    // Replace operand
                    val woIndex = predicate.remove(i)
                    operands[i].accept(
                            createInjector(atomics, and, or,
                                    Consumer { newPredicate -> consumer.accept(woIndex.or(newPredicate)) }))
                }
            }

            // No injection inside NOT
            override fun visitNot(predicate: NotPredicate<T>) {
                process(predicate)
            }

            override fun visitParenthesisPredicate(predicate: ParenthesesPredicate<T>) {
                process(predicate)
                predicate.operand.accept(createInjector(atomics, and, or, Consumer { consumer.accept(it) }))
            }

            override fun visit(predicate: Predicate<T>) {
                process(predicate)
            }

            private fun process(predicate: Predicate<T>) {
                atomics.forEach {
                    if (or) {
                        consumer.accept(predicate.or(it))
                    }
                    if (and) {
                        consumer.accept(predicate.and(it))
                    }
                }
            }
        }
    }
}