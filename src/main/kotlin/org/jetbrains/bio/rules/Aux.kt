package org.jetbrains.bio.rules

import org.apache.commons.math3.util.CombinatoricsUtils
import org.jetbrains.bio.predicates.Predicate
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Auxiliary info for visualization purposes.
 *
 * @param rule represents joint distribution (condition, parent?, target)
 * @param target represents all the top level predicates pairwise combinations
 */
data class Aux(val rule: Combinations, val target: Upset? = null)


/**
 * Data class describing joint combinations.
 * @param names list of binary predicates names over given database
 * @param combinations combinations in binary bit-wise encoding order
 */
@Suppress("MemberVisibilityCanBePrivate")
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

data class UpsetRecord(val id: List<Int>, val n: Int) {
    override fun toString(): String {
        return "${id.joinToString(",") { it.toString() }}:$n"
    }
}

/**
 * Data class describing joint combinations, see UpsetR for details.
 * @param names list of binary predicates names over given database
 * @param data map : items -> intersection
 */
@Suppress("MemberVisibilityCanBePrivate")
data class Upset(val names: List<String>, val data: List<UpsetRecord>) {
    companion object {
        fun <T> of(database: List<T>, predicates: List<Predicate<T>>, target: Predicate<T>,
                   k: Int = 3,
                   combinations: Int = 50): Upset {
            check(k <= 5) {
                "Too big combinations size"
            }
            val data = arrayListOf<UpsetRecord>()
            for (kI in 1..k) {
                CombinatoricsUtils.combinationsIterator(predicates.size + 1, kI).forEach { c ->
                    val result = Predicate.and(c.map { if (it == 0) target else predicates[it - 1] }).test(database)
                    if (!result.isEmpty) {
                        data.add(UpsetRecord(c.toList(), result.cardinality()))
                    }
                }
            }
            // Include target, take max combinations
            data.sortByDescending { (if (0 in it.id) 1e10 else 0.0) + it.n }
            return Upset(listOf(target.name()) + predicates.map { it.name() }, data.take(combinations))
        }
    }
}
