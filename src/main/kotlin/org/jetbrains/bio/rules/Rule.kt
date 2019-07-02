package org.jetbrains.bio.rules

import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.predicates.PredicateParser
import java.lang.Math.pow
import kotlin.math.sqrt

/**
 * Association Rule
 */
class Rule<T>(val conditionPredicate: Predicate<T>,
              val targetPredicate: Predicate<T>,
              val database: Int,
              val condition: Int,
              val target: Int,
              val intersection: Int) {

    val name: String = "${conditionPredicate.name()} ${PredicateParser.IMPL} ${targetPredicate.name()}"

    val errorType1: Int = condition - intersection

    val errorType2: Int = target - intersection

    init {
        // Check invariants
        check(condition <= database) { "$name: Condition: $condition > database: $database" }
        check(target <= database) { "$name: Target: $target > database: $database" }
        check(condition >= intersection) { "$name: Condition: $condition < support: $intersection" }
        check(target >= intersection) { "$name: Target: $target < support: $intersection" }
    }

    constructor(conditionPredicate: Predicate<T>,
                targetPredicate: Predicate<T>,
                data: List<T>) :
            this(conditionPredicate, targetPredicate, data.size,
                    conditionPredicate.test(data).cardinality(),
                    targetPredicate.test(data).cardinality(),
                    conditionPredicate.and(targetPredicate).test(data).cardinality())

    /**
     * Lift measures how many times more often X and Y occur together than expected if they were statistically independent.

     * See: http://michael.hahsler.net/research/association_rules/measures.html#lift
     */
    val lift: Double = if (condition == 0 || target == 0)
        0.0
    else
        1.0 * database * intersection / (condition * target)


    /**
     * Conviction was developed as an alternative to confidence which was found to
     * not capture direction of associations adequately. Conviction compares the probability that X
     * appears without Y if they were dependent with the actual frequency of the appearance of X without Y.
     * In that respect it is similar to lift (see section about lift on this page), however,
     * it contrast to lift it is a directed measure since it also uses the information of the absence of the consequent.
     * An interesting fact is that conviction is monotone in confidence and lift.
     * See: http://michael.hahsler.net/research/association_rules/measures.html#conviction
     *
     * Conviction metrics is used as the most effective metrics for predictive tasks.
     * Alipio Paulo J. Azevedo. Comparing rule measures for predictive association records.
     * Machine Learning: ECML, 4701:510–517, 2007.
     *
     * conviction(X -> Y) = (1-supp(Y))/(1-conf(X -> Y)) = P(X)P(not Y)/P(X and not Y) = P(not Y)/P(not Y | X)
     */
    val conviction: Double = 1.0 * condition / database * (database - target) / (errorType1 + 1.0)

    /**
     * Scenario1: The expert Er tolerates the appearance of a certain number of counter-examples
     * X ∩ ¬Y to a decision rule. In this case, the rejection of a rule is postponed until enough
     * counter-examples are found.
     *
     * Scenario2: The expert Er refuses the appearance of too many counter-examples to a
     * decision rule. The rejection of the rule must be done rapidly with respect to the number of
     * counter-examples.
     *
     * Conviction works perfectly in Scenario1. LOE is well placed in both scenarios. It stands for a good compromise.
     *
     * LOE(X -> Y) = (n*sup(X∩Y)−sup(X)sup(Y)) / sup(X)sup(¬Y) = (p(Y|X) - P(Y)) / P(not Y)
     * Important: in case A < B < C, LOE(A => C) == LOE(B => C). We use pow to fix this.
     */
    val loe: Double = (1.0 * database * pow(intersection.toDouble(), 1.1) / (condition + 1.0) - target) / (database - target + 1.0)

    /**
     * Computes correlation between condition and target.
     * In case of binary values this can be estimated as Phi coefficient: https://en.wikipedia.org/wiki/Phi_coefficient
     * NOTE: coefficient is NaN in case when any of the values have zero variance.
     * @return value in range [-1, 1] or NaN
     */
    val correlation: Double by lazy {
        val n11 = intersection
        val n00 = database - condition - target + intersection
        val n10 = errorType1
        val n01 = errorType2
        val n1_ = condition
        val n0_ = database - condition
        val n_1 = target
        val n_0 = database - target
        val phi = (1.0 * n11.toDouble() * n00.toDouble() - 1.0 * n10.toDouble() * n01.toDouble()) /
                sqrt(1.0 * n1_.toDouble() * n0_.toDouble() * n_1.toDouble() * n_0.toDouble())
        // NaN in case when any of the values have zero variance
        check(phi.isNaN() || -1 <= phi && phi <= 1) { phi }
        phi
    }

    override fun toString(): String = name

    /**
     * These are consistent with [RuleRecord.toMap]
     */
    fun toRecord(id: String = ""): RuleRecord<T> =
            RuleRecord(id, conditionPredicate, targetPredicate,
                    database, condition, target, intersection,
                    condition.toDouble() / database, intersection.toDouble() / condition,
                    correlation, lift, conviction, loe,
                    conditionPredicate.complexity())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rule<*>) return false

        if (conditionPredicate != other.conditionPredicate) return false
        if (targetPredicate != other.targetPredicate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = conditionPredicate.hashCode()
        result = 31 * result + targetPredicate.hashCode()
        return result
    }


}

class RuleRecord<T>(val id: String, val conditionPredicate: Predicate<T>, val targetPredicate: Predicate<T>,
                    val database: Int, val condition: Int, val target: Int, val intersection: Int,
                    val support: Double, val confidence: Double,
                    val correlation: Double,
                    val lift: Double,
                    val conviction: Double,
                    val loe: Double,
                    val complexity: Int) {

    fun toCSV() = listOf(
            id,
            conditionPredicate.name(),
            targetPredicate.name(),
            database,
            condition,
            target,
            intersection,
            support,
            confidence,
            if (!correlation.isNaN()) correlation else 0.0,
            if (!lift.isNaN()) lift else 0.0,
            if (!conviction.isNaN()) conviction else 0.0,
            if (!loe.isNaN()) loe else 0.0,
            complexity)


    fun toMap() = PARAMS.zip(toCSV()).toMap()

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_CONDITION = "condition"
        private const val KEY_TARGET = "target"
        private const val KEY_DATABASE_COUNT = "database_count"
        private const val KEY_CONDITION_COUNT = "condition_count"
        private const val KEY_TARGET_COUNT = "target_count"
        private const val KEY_INTERSECTION_COUNT = "intersection_count"
        private const val KEY_SUPPORT = "support"
        private const val KEY_CONFIDENCE = "confidence"
        private const val KEY_CORRELATION = "correlation"
        private const val KEY_LIFT = "lift"
        private const val KEY_CONVICTION = "conviction"
        private const val KEY_LOE = "loe"
        private const val KEY_COMPLEXITY = "complexity"

        val PARAMS = listOf(
                KEY_ID,
                KEY_CONDITION,
                KEY_TARGET,
                KEY_DATABASE_COUNT,
                KEY_CONDITION_COUNT,
                KEY_TARGET_COUNT,
                KEY_INTERSECTION_COUNT,
                KEY_SUPPORT,
                KEY_CONFIDENCE,
                KEY_CORRELATION,
                KEY_LIFT,
                KEY_CONVICTION,
                KEY_LOE,
                KEY_COMPLEXITY)


        fun <T> fromCSV(it: CSVRecord, function: (String) -> Predicate<T>): RuleRecord<T> {
            return RuleRecord(id = s(it, KEY_ID),
                    conditionPredicate = PredicateParser.parse(s(it, KEY_CONDITION), function)!!,
                    targetPredicate = PredicateParser.parse(s(it, KEY_TARGET), function)!!,
                    database = i(it, KEY_DATABASE_COUNT),
                    condition = i(it, KEY_CONDITION_COUNT), target = i(it, KEY_TARGET_COUNT),
                    intersection = i(it, KEY_INTERSECTION_COUNT),
                    support = d(it, KEY_SUPPORT), confidence = d(it, KEY_CONFIDENCE),
                    correlation = d(it, KEY_CORRELATION),
                    lift = d(it, KEY_LIFT),
                    conviction = d(it, KEY_CONVICTION),
                    loe = d(it, KEY_LOE),
                    complexity = i(it, KEY_COMPLEXITY))
        }

        private fun s(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s] else ""
        private fun d(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toDouble() else 0.0
        private fun i(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toInt() else 0

    }
}