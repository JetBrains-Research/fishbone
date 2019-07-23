package org.jetbrains.bio.rules.validation

import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.AndPredicate
import org.jetbrains.bio.predicates.OrPredicate
import org.jetbrains.bio.predicates.Predicate
import org.jetbrains.bio.rules.Rule

abstract class RuleSignificanceCheck {

    /**
     * Returns pvalue for corresponding Null Hypothesis
     */
    abstract fun <T> testRule(rule: Rule<T>, database: List<T>): Double

    protected fun <T> buildSources(rule: Rule<T>): List<Predicate<T>> {
        return when (val conditionPredicate = rule.conditionPredicate) {
            is AndPredicate -> conditionPredicate.operands
            is OrPredicate -> {
                val operands = conditionPredicate.operands
                if (operands.size < 2) {
                    throw IllegalArgumentException("Operands size is less than 2") //TODO: refactor
                }
                val initial = operands[0].not()
                listOf(operands.drop(1).map { it.not() }.fold(initial, { acc, p -> acc.and(p.not()) }).not())
            }
            else -> listOf(conditionPredicate)
        }
    }

    protected fun <T> aFreq(sources: List<Predicate<T>>, target: Predicate<T>, database: List<T>) =
            AndPredicate(sources + target).test(database).cardinality()

    protected fun <T> bFreq(sources: List<Predicate<T>>, target: Predicate<T>, database: List<T>) =
            AndPredicate(sources + target.not()).test(database).cardinality()

    protected fun <T> cFreq(reducedSources: List<Predicate<T>>, x: Predicate<T>, target: Predicate<T>, database: List<T>) =
            AndPredicate(reducedSources + x.not() + target).test(database).cardinality()

    protected fun <T> dFreq(reducedSources: List<Predicate<T>>, x: Predicate<T>, target: Predicate<T>, database: List<T>) =
            AndPredicate(reducedSources + x.not() + target.not()).test(database).cardinality()

    companion object {

        private const val SMALL_DATABASE_SIZE_THRESHOLD = 10000
        private val logger = Logger.getLogger(RuleSignificanceCheck::class.java)

        fun <T> test(rule: Rule<T>, database: List<T>): Double {
            logger.debug("Testing rule's significance: $rule")
            return if (database.size > SMALL_DATABASE_SIZE_THRESHOLD) {
                ChiSquaredCheck().testRule(rule, database)
            } else {
                ChiSquaredCheck().testRule(rule, database)
                // Fisher exact test is too strict for now. Uncomment later
                //FisherExactCheck().testRule(rule, database)
            }
        }

    }

}