package org.jetbrains.bio.rules

import com.google.gson.GsonBuilder
import gnu.trove.map.TLongDoubleMap
import gnu.trove.map.TObjectIntMap
import gnu.trove.map.hash.TLongDoubleHashMap
import gnu.trove.map.hash.TObjectIntHashMap
import org.apache.commons.math3.util.Precision
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.*
import org.jetbrains.bio.statistics.distribution.Sampling

/**
 * Probe predicate is used as an evaluation of rule mining algorithm as an effective feature selection
 * “Causal Feature Selection” I. Guyon et al., "Computational Methods of Feature Selection", 2007.
 * http://clopinet.com/isabelle/Papers/causalFS.pdf
 */
class ProbePredicate<T>(private val name: String, database: List<T>) : Predicate<T>() {
    // 0.5 probability is a good idea, because of zero information,
    // each subset is going to have similar coverage fraction.
    private val trueSet = database.filter { Sampling.sampleBernoulli(0.5) }.toSet()

    override fun test(item: T): Boolean = item in trueSet
    override fun name() = name
}


/**
 * For discrete probability distributions P and Q, the Kullback–Leibler divergence is
 * the expectation of the logarithmic difference between the probabilities P and Q,
 * where the expectation is taken using the probabilities P.
 * The Kullback–Leibler divergence is defined *only* if Q(i)=0 implies P(i)=0, for all i (absolute continuity).
 * Whenever P(i) is zero the contribution of the i-th term is interpreted as zero because in case x -> 0, xlog(x) -> 0
 */
fun <T> KL(p: Distribution<T>, q: Distribution<T>): Double {
    check(p.atomics == q.atomics) { "Different atomics!" }
    check(p.database == q.database) { "Different databased!" }
    var kl = 0.0
    var v = 0L
    while (v < (1L shl p.atomics.size)) {
        val pV = p.probability(v)
        val qV = q.probability(v)
        if (qV == 0.0) {
            check(pV <= 1e-6) { "Zero probability problem: Q p($v) = $qV, but P p($v) = $pV" }
        }
        // In case x -> 0, xlog(x) -> 0.
        if (pV != 0.0) {
            kl += pV * Math.log(pV / qV)
        }
        v++
    }
    // Fix potential floating point errors
    return Math.max(0.0, kl)
}

class EmpiricalDistribution<T>(database: List<T>, atomics: List<Predicate<T>>) : Distribution<T>(database, atomics) {

    init {
        val tests = atomics.map { it.test(database) }
        val p = 1.0 / database.size
        database.indices.forEach { index ->
            var v = 0L
            tests.forEachIndexed { i, test ->
                if (test[index])
                    v = v or (1L shl i)
            }
            probabilities.adjustOrPutValue(v, p, p)
        }
    }

    override fun probability(v: Long): Double {
        return probabilities.get(v)
    }

    override fun marginals(): DoubleArray = empiricalMarginals(atomics, database)

}

open class Distribution<T>(val database: List<T>,
                           val atomics: List<Predicate<T>>,
                           private val atomicsIndices: TObjectIntMap<Predicate<T>> = TObjectIntHashMap<Predicate<T>>().apply {
                               atomics.forEachIndexed { i, p -> this.put(p, i) }
                           },
                           protected val probabilities: TLongDoubleMap = TLongDoubleHashMap()) {
    init {
        check(atomics.size < 20) {
            "Maximum number of items which can be encoded as long"
        }
    }

    open fun marginals(): DoubleArray {
        val result = DoubleArray(atomics.size)
        var v = 0L
        while (v < 1 shl atomics.size) {
            val p = probability(v)
            result.indices.forEach {
                if (v and (1L shl it) != 0L) {
                    result[it] += p
                }
            }
            v++
        }
        return result
    }

    open fun probability(v: Long): Double {
        if (!probabilities.containsKey(v)) {
            probabilities.put(v, probabilityIndependent(v))
        }
        return probabilities[v]
    }

    private fun probabilityIndependent(v: Long): Double =
            empiricalMarginals(atomics, database)
                    .mapIndexed { i, p -> if (v and (1L shl i) != 0L) p else 1.0 - p }
                    .fold(1.0) { a, b -> a * b }


    /**
     * Entropy function
     */
    fun H(): Double {
        var info = 0.0
        var v = 0L
        while (v < 1 shl atomics.size) {
            info += xlog(probability(v))
            v++
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
        var v = 0L
        while (v < 1 shl atomics.size) {
            val cond = eval(rule.conditionPredicate, v, atomicsIndices)
            val targ = eval(rule.targetPredicate, v, atomicsIndices)
            val p = probability(v)
            if (cond && targ) {
                tpSum += p
            } else if (cond && !targ) {
                fpSum += p
            } else if (!cond && targ) {
                fnSum += p
            } else {
                tnSum += p
            }
            v++
        }

        // Check that summary probability = 1
        LOG.debug("Prior FP = $fpSum\tTP = $tpSum\tFN = $fnSum\tTN = $tnSum")
        check(Precision.equals(fpSum + fnSum + tpSum + tnSum, 1.0, 1e-10)) {
            "Illegal Prior: fpSum($fpSum) + fnSum($fnSum) + tpSum($tpSum) + tnSum($tnSum) = ${fpSum + fnSum + tpSum + tnSum}"
        }

        // Update distribution according to rule params
        val fp = rule.errorType1.toDouble() / rule.database
        val fn = rule.errorType2.toDouble() / rule.database
        val tp = rule.intersection.toDouble() / rule.database
        val tn = 1.0 - (fp + fn + tp)
        LOG.debug("Rule: ${rule.name}\tFP = $fp\tTP = $tp\tFN = $fn\tTN = $tn")
        val updatedProbabilities = TLongDoubleHashMap(probabilities)
        v = 0L
        while (v < 1 shl atomics.size) {
            val cond = eval(rule.conditionPredicate, v, atomicsIndices)
            val targ = eval(rule.targetPredicate, v, atomicsIndices)
            val p = probability(v)
            if (cond && targ) {
                updatedProbabilities.put(v, if (tpSum != 0.0) p * (tp / tpSum) else 0.0)
            } else if (cond && !targ) {
                updatedProbabilities.put(v, if (fpSum != 0.0) p * (fp / fpSum) else 0.0)
            } else if (!cond && targ) {
                updatedProbabilities.put(v, if (fnSum != 0.0) p * (fn / fnSum) else 0.0)
            } else {
                updatedProbabilities.put(v, if (tnSum != 0.0) p * (tn / tnSum) else 0.0)
            }
            v++
        }
        return Distribution(database, atomics, atomicsIndices, updatedProbabilities)
    }

    // Empirical Bayes
    protected fun <T> empiricalMarginals(atomics: List<Predicate<T>>, database: List<T>) =
            DoubleArray(atomics.size) { atomics[it].test(database).cardinality().toDouble() / database.size }

    private fun xlog(x: Double): Double = if (x == 0.0) 0.0 else x * Math.log(x)

    override fun toString(): String {
        return GsonBuilder().setPrettyPrinting().create().toJson(toJson())
    }

    fun toJson(): Map<String, Any> {
        return mapOf(
                // Preserve order here
                "marginals" to linkedMapOf(*atomics.map { it to marginals()[atomics.indexOf(it)] }.toTypedArray()),
                "probabilities" to (0.until(1L shl atomics.size)).associate { v ->
                    "${atomics.indices.joinToString("") { (v and (1L shl it) != 0L).mark().toString() }}" to
                            probability(v)
                })
    }

    companion object {
        private val LOG = Logger.getLogger(Distribution::class.java)
    }
}

/**
 * Evaluate predicate using atomics set to given values.
 */
private fun <T> eval(p: Predicate<T>, v: Long, atomicsIndices: TObjectIntMap<Predicate<T>>): Boolean {
    var result = false
    p.accept(object : PredicateVisitor<T>() {
        override fun visitNot(predicate: NotPredicate<T>) {
            result = !eval(predicate.operand, v, atomicsIndices)
        }

        override fun visitAndPredicate(predicate: AndPredicate<T>) {
            result = predicate.operands.all { eval(it, v, atomicsIndices) }
        }

        override fun visitOrPredicate(predicate: OrPredicate<T>) {
            result = predicate.operands.any { eval(it, v, atomicsIndices) }
        }

        override fun visit(predicate: Predicate<T>) {
            result = v and (1L shl atomicsIndices.get(predicate)) != 0L
        }
    })
    return result
}