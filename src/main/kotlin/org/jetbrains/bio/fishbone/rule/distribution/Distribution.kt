package org.jetbrains.bio.fishbone.rule.distribution

import com.google.common.primitives.Doubles
import gnu.trove.map.TObjectIntMap
import gnu.trove.map.hash.TObjectIntHashMap
import org.apache.commons.math3.util.Precision
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.slf4j.LoggerFactory
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max

/**
 * Joint distribution of binary predicates over database.
 */
open class Distribution<T>(
    val database: List<T>,
    val predicates: List<Predicate<T>>,
    protected val probabilities: DoubleArray = createProbabilitiesArray(predicates.size),
    private val indices: TObjectIntMap<Predicate<T>> = TObjectIntHashMap<Predicate<T>>().apply {
        predicates.forEachIndexed { i, p -> this.put(p, i) }
    }
) {

    /**
     * Probability of predicates combination encoded in bit-wise representation with int values.
     */
    open fun probability(encoding: Int): Double {
        if (!Doubles.isFinite(probabilities[encoding])) {
            probabilities[encoding] = probabilityIndependent(encoding)
        }
        return probabilities[encoding]
    }

    private fun probabilityIndependent(encoding: Int): Double =
        empiricalMarginals(predicates, database)
            .mapIndexed { i, p -> if (encoding and (1 shl i) != 0) p else 1.0 - p }
            .fold(1.0) { a, b -> a * b }


    /**
     * Entropy function
     */
    fun H(): Double {
        var info = 0.0
        var encoding = 0
        while (encoding < 1 shl predicates.size) {
            info += xlog(probability(encoding))
            encoding++
        }
        return -info
    }

    /**
     * Update distribution according to given rule, i.e. TP, FP, TN, FN proportions.
     */
    fun learn(rule: Rule<T>): Distribution<T> {
        var fpSum = 0.0
        var fnSum = 0.0
        var tpSum = 0.0
        var tnSum = 0.0
        var encoding = 0
        while (encoding < 1 shl predicates.size) {
            val cond = rule.conditionPredicate.eval(encoding, indices)
            val targ = rule.targetPredicate.eval(encoding, indices)
            val p = probability(encoding)
            if (cond && targ) {
                tpSum += p
            } else if (cond && !targ) {
                fpSum += p
            } else if (!cond && targ) {
                fnSum += p
            } else {
                tnSum += p
            }
            encoding++
        }

        // Check that summary probability = 1
        LOG.debug("Prior FP = $fpSum\tTP = $tpSum\tFN = $fnSum\tTN = $tnSum")
        check(Precision.equals(fpSum + fnSum + tpSum + tnSum, 1.0, 1e-10)) {
            "Illegal Prior: fpSum($fpSum) + fnSum($fnSum) + tpSum($tpSum) + tnSum($tnSum) = " +
                    "${fpSum + fnSum + tpSum + tnSum}"
        }

        // Update distribution according to rule params
        val fp = rule.errorType1.toDouble() / rule.database
        val fn = rule.errorType2.toDouble() / rule.database
        val tp = rule.intersection.toDouble() / rule.database
        val tn = 1.0 - (fp + fn + tp)
        LOG.debug("Rule: ${rule.name}\tFP = $fp\tTP = $tp\tFN = $fn\tTN = $tn")
        val updatedProbabilities = DoubleArray(1 shl predicates.size)
        encoding = 0
        while (encoding < 1 shl predicates.size) {
            val cond = rule.conditionPredicate.eval(encoding, indices)
            val targ = rule.targetPredicate.eval(encoding, indices)
            val p = probability(encoding)
            if (cond && targ) {
                updatedProbabilities[encoding] = if (tpSum != 0.0) p * (tp / tpSum) else 0.0
            } else if (cond && !targ) {
                updatedProbabilities[encoding] = if (fpSum != 0.0) p * (fp / fpSum) else 0.0
            } else if (!cond && targ) {
                updatedProbabilities[encoding] = if (fnSum != 0.0) p * (fn / fnSum) else 0.0
            } else {
                updatedProbabilities[encoding] = if (tnSum != 0.0) p * (tn / tnSum) else 0.0
            }
            encoding++
        }
        return Distribution(database, predicates, updatedProbabilities, indices)
    }

    // Empirical Bayes
    private fun <T> empiricalMarginals(predicates: List<Predicate<T>>, database: List<T>) =
        DoubleArray(predicates.size) { predicates[it].test(database).cardinality().toDouble() / database.size }

    private fun xlog(x: Double): Double = if (x == 0.0) 0.0 else x * ln(x)

    companion object {
        private val LOG = LoggerFactory.getLogger(Distribution::class.java)

        private fun createProbabilitiesArray(n: Int): DoubleArray {
            check(n < log2(Int.MAX_VALUE.toDouble())) {
                "Maximum number of items which can be encoded as int exceeded $n"
            }
            return DoubleArray(1 shl n) { Double.NaN }
        }

        /**
         * Kullback-Leibler divergence for discrete statistical distributions.
         *
         * For discrete probability distributions P and Q, the Kullback–Leibler divergence is
         * the expectation of the logarithmic difference between the probabilities P and Q,
         * where the expectation is taken using the probabilities P.
         * The Kullback–Leibler divergence is defined *only* if Q(i)=0 implies P(i)=0, for all i (absolute continuity).
         * Whenever P(i) is zero the contribution of the i-th term is interpreted as zero, when x -> 0, xlog(x) -> 0
         */
        fun <T> kullbackLeibler(p: Distribution<T>, q: Distribution<T>): Double {
            check(p.predicates == q.predicates) { "Different atomics!" }
            check(p.database == q.database) { "Different databased!" }
            var kl = 0.0
            var encoding = 0
            while (encoding < (1 shl p.predicates.size)) {
                val pV = p.probability(encoding)
                val qV = q.probability(encoding)
                if (qV == 0.0) {
                    check(pV <= 1e-6) { "Zero probability problem: Q p($encoding) = $qV, but P p($encoding) = $pV" }
                }
                // In case x -> 0, xlog(x) -> 0.
                if (pV != 0.0) {
                    kl += pV * ln(pV / qV)
                }
                encoding++
            }
            // Fix potential floating point errors
            return max(0.0, kl)
        }
    }
}
