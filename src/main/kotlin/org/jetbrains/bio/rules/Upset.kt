package org.jetbrains.bio.rules

import org.jetbrains.bio.predicates.Predicate
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Data class describing joint combinations, see UpsetR for details.
 * @param names list of binary predicates names over given database
 * @param combinations combinations in binary bit-wise encoding order
 */
@Suppress("MemberVisibilityCanBePrivate")
class Upset(val names: List<String>, val combinations: List<Int>) {
    companion object {
        fun <T> of(database: List<T>, predicates: List<Predicate<T>>): Upset {
            check(predicates.size < log2(Int.MAX_VALUE.toDouble())) {
                "Maximum number of items which can be encoded as int exceeded ${predicates.size}"
            }
            val names = predicates.map { it.name() }
            val empiricalDistribution = EmpiricalDistribution(database, predicates)
            val combinations = (0 until (1 shl predicates.size)).map {
                (database.size * empiricalDistribution.probability(it)).roundToInt()
            }
            return Upset(names, combinations)
        }
    }
}
