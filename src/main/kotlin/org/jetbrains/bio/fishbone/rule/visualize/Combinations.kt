package org.jetbrains.bio.fishbone.rule.visualize

import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.rule.distribution.EmpiricalDistribution
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Data class describing joint combinations.
 * @param names list of binary predicates names over given database
 * @param combinations combinations in binary bit-wise encoding order
 */
data class Combinations(val names: List<String>, val combinations: List<Int>) {
    companion object {
        fun <T> of(database: List<T>, predicates: List<Predicate<T>>): Combinations {
            check(predicates.size < log2(Int.MAX_VALUE.toDouble())) {
                "Maximum number of items which can be encoded as int exceeded ${predicates.size}"
            }
            val names = predicates.map { it.name() }
            val empiricalDistribution = EmpiricalDistribution(database, predicates)
            val combinations = (0 until (1 shl predicates.size)).map {
                (database.size * empiricalDistribution.probability(it)).roundToInt()
            }
            return Combinations(names, combinations)
        }
    }
}