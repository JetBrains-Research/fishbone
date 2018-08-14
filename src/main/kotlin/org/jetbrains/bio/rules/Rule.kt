package org.jetbrains.bio.rules

import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.predicates.PredicateParser

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
                    conditionPredicate.and(targetPredicate).test(data).cardinality()) {
    }

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
                Math.sqrt(1.0 * n1_.toDouble() * n0_.toDouble() * n_1.toDouble() * n_0.toDouble())
        // NaN in case when any of the values have zero variance
        check(phi.isNaN() || -1 <= phi && phi <= 1) { phi }
        phi
    }

    override fun toString(): String = name

    /**
     * These are consistent with [RuleRecord.PARAMS]
     */
    fun toRecord(id: String = ""): RuleRecord<T> =
            RuleRecord(id, conditionPredicate, targetPredicate,
                    database, condition, target, intersection, errorType1, errorType2,
                    condition.toDouble() / database, intersection.toDouble() / condition,
                    correlation, conviction,
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
                    val errorType1: Int, val errorType2: Int,
                    support: Double, confidence: Double,
                    correlation: Double, conviction: Double,
                    val complexity: Int) {
    val support: Double = if (!support.isNaN()) support else 0.0
    val confidence: Double = if (!confidence.isNaN()) confidence else 0.0
    val correlation: Double = if (!correlation.isNaN()) correlation else 0.0
    val conviction: Double = if (!conviction.isNaN()) conviction else 0.0

    fun toCSV() =
            listOf(id,
                    conditionPredicate.name(), targetPredicate.name(),
                    database.toString(), condition.toString(), target.toString(), intersection.toString(),
                    errorType1.toString(), errorType2.toString(),
                    "%.6f".format(support), "%.6f".format(confidence),
                    "%.6f".format(correlation), "%.6f".format(conviction),
                    conditionPredicate.complexity().toString())

    companion object {
        val PARAMS = listOf("id",
                "condition_name", "target_name",
                "database", "condition", "target", "intersection",
                "error_type_1", "error_type_2",
                "support", "confidence",
                "correlation", "conviction",
                "complexity")

        fun <T> fromCSV(it: CSVRecord, function: (String) -> Predicate<T>): RuleRecord<T> {
            return RuleRecord(id = s(it, "id"),
                    conditionPredicate = PredicateParser.parse(s(it, "condition_name"), function)!!,
                    targetPredicate = PredicateParser.parse(s(it, "target_name"), function)!!,
                    database = i(it, "database"), condition = i(it, "condition"), target = i(it, "target"),
                    intersection = i(it, "intersection"),
                    errorType1 = i(it, "error_type_1"), errorType2 = i(it, "error_type_2"),
                    support = d(it, "support"), confidence = d(it, "confidence"),
                    correlation = d(it, "correlation"), conviction = d(it, "conviction"),
                    complexity = i(it, "complexity"))
        }

        private fun s(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s] else ""
        private fun d(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toDouble() else 0.0
        private fun i(it: CSVRecord, s: String) = if (it.isMapped(s)) it[s].toInt() else 0

    }
}