package org.jetbrains.bio.fishbone.rule.distribution

import org.jetbrains.bio.fishbone.predicate.Predicate

class EmpiricalDistribution<T>(database: List<T>, predicates: List<Predicate<T>>) :
    Distribution<T>(database, predicates) {

    init {
        probabilities.fill(0.0)
        val tests = predicates.map { it.test(database) }
        val p = 1.0 / database.size
        database.indices.forEach { index ->
            var encoding = 0
            tests.forEachIndexed { i, test ->
                if (test[index])
                    encoding = encoding or (1 shl i)
            }
            probabilities[encoding] += p
        }
    }
}