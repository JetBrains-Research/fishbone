package org.jetbrains.bio.fishbone.rule.distribution

import gnu.trove.map.TObjectIntMap
import org.jetbrains.bio.fishbone.predicate.*

/**
 * Evaluate predicate setting given values to sub predicates.
 * @param encoding Long value encoding atomic sub predicates values.
 * @param indices Indices from predicates to encoding index bite.
 */
fun <T> Predicate<T>.eval(encoding: Int, indices: TObjectIntMap<Predicate<T>>): Boolean {
    var result = false
    accept(object : PredicateVisitor<T>() {
        override fun visitNot(predicate: NotPredicate<T>) {
            if (indices.containsKey(predicate)) {
                visit(predicate)
            } else {
                result = !predicate.operand.eval(encoding, indices)
            }

        }

        override fun visitAndPredicate(predicate: AndPredicate<T>) {
            if (indices.containsKey(predicate)) {
                visit(predicate)
            } else {
                result = predicate.operands.all { it.eval(encoding, indices) }
            }
        }

        override fun visitOrPredicate(predicate: OrPredicate<T>) {
            if (indices.containsKey(predicate)) {
                visit(predicate)
            } else {
                result = predicate.operands.any { it.eval(encoding, indices) }
            }
        }

        override fun visit(predicate: Predicate<T>) {
            check(indices.containsKey(predicate)) {
                "Missing predicate ${predicate.name()} in indices"
            }
            result = encoding and (1 shl indices.get(predicate)) != 0
        }
    })
    return result
}
