package org.jetbrains.bio.rules

import gnu.trove.map.hash.TObjectDoubleHashMap
import org.apache.commons.math3.util.Precision
import org.apache.log4j.Logger
import org.jetbrains.bio.predicates.*
import org.jetbrains.bio.statistics.data.BitterSet
import org.jetbrains.bio.statistics.distribution.Sampling
import java.util.*

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
    val v = BitterSet(p.atomics.size)
    var kl = 0.0
    do {
        val pV = p.probability(v)
        val qV = q.probability(v)
        if (qV == 0.0) {
            check(pV <= 1e-6) { "Zero probability problem: Q p($v) = $qV, but P p($v) = $pV" }
        }
        // In case x -> 0, xlog(x) -> 0.
        if (pV != 0.0) {
            kl += pV * Math.log(pV / qV)
        }
    } while (next(v))
    return kl
}

class EmpiricalDistribution<T>(database: List<T>, atomics: List<Predicate<T>>) : Distribution<T>(database, atomics) {

    init {
        val tests = atomics.map { it.test(database) }
        val p = 1.0 / database.size
        database.indices.forEach { index ->
            val v = BitterSet(atomics.size)
            tests.forEachIndexed { i, test -> if (test[index]) v[i] = true }
            probabilities.adjustOrPutValue(v, p, p)
        }
    }

    override fun probability(v: BitterSet): Double {
        return if (v in probabilities) probabilities.get(v) else 0.0
    }

    override fun marginals(): List<Double> = empiricalMarginals(atomics, database)

}

open class Distribution<T>(val database: List<T>,
                           val atomics: List<Predicate<T>>,
                           protected val probabilities: TObjectDoubleHashMap<BitterSet> = TObjectDoubleHashMap()) {

    open fun marginals(): List<Double> {
        val result = atomics.map { 0.0 }.toMutableList()
        val v = BitterSet(atomics.size)
        do {
            val p = probability(v)
            result.indices.forEach {
                if (v[it]) {
                    result[it] += p
                }
            }
        } while (next(v))
        return result
    }

    open fun probability(v: BitterSet): Double {
        if (v !in probabilities) {
            // Important, otherwise we change keys.
            probabilities.put(v.copy(), probabilityIndependent(v))
        }
        return probabilities[v]
    }

    fun probabilityIndependent(v: BitterSet): Double =
            empiricalMarginals(atomics, database)
                    .mapIndexed { i, p -> if (v[i]) p else 1.0 - p }
                    .fold(1.0) { a, b -> a * b }


    /**
     * Entropy function
     */
    fun H(): Double {
        var info = 0.0
        val v = BitterSet(atomics.size)
        do {
            info += xlog(probability(v))
        } while (next(v))
        return -info
    }

    /**
     * Update distribution according to given rule, i.e. TP, FP, TN, FN proportions.
     */
    fun learn(rule: Rule<T>): Distribution<T> {
        var v = BitterSet(atomics.size)
        var fpSum = 0.0
        var fnSum = 0.0
        var tpSum = 0.0
        var tnSum = 0.0
        do {
            val cond = eval(rule.conditionPredicate, v, atomics)
            val targ = eval(rule.targetPredicate, v, atomics)
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
        } while (next(v))

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
        val updatedProbabilities = TObjectDoubleHashMap<BitterSet>(probabilities)
        v = BitterSet(atomics.size)
        do {
            val cond = eval(rule.conditionPredicate, v, atomics)
            val targ = eval(rule.targetPredicate, v, atomics)
            val p = probability(v)
            if (cond && targ) {
                updatedProbabilities.put(v.copy(), if (tpSum != 0.0) p * (tp / tpSum) else 0.0)
            } else if (cond && !targ) {
                updatedProbabilities.put(v.copy(), if (fpSum != 0.0) p * (fp / fpSum) else 0.0)
            } else if (!cond && targ) {
                updatedProbabilities.put(v.copy(), if (fnSum != 0.0) p * (fn / fnSum) else 0.0)
            } else {
                updatedProbabilities.put(v.copy(), if (tnSum != 0.0) p * (tn / tnSum) else 0.0)
            }
        } while (next(v))
        return Distribution(database, atomics, updatedProbabilities)
    }

    // Empirical Bayes
    protected fun <T> empiricalMarginals(atomics: List<Predicate<T>>, database: List<T>) =
            atomics.map { it.test(database).cardinality().toDouble() / database.size }

    private fun xlog(x: Double): Double = if (x == 0.0) 0.0 else x * Math.log(x)

    override fun toString(): String {
        var result = "Marginals:\n" +
                "${atomics.indices.map { "${atomics[it].name()}\t${marginals()[it]}" }.joinToString("\n")}\n" +
                "Probabilities:\n"
        val v = BitterSet(atomics.size)
        do {
            result += "{${atomics.indices.filter { v[it] }
                    .map { atomics[it].name() }
                    .joinToString(", ")}}\t${probability(v)}\n"
        } while (next(v))
        return result + "H = ${H()}"
    }

    companion object {
        private val LOG = Logger.getLogger(Distribution::class.java)
    }
}

/**
 * Switch to next binary mask if possible.
 */
fun next(v: BitterSet): Boolean {
    for (i in (0 until v.size()).reversed()) {
        if (!v[i]) {
            v[i] = true
            for (j in i + 1 until v.size()) {
                v[j] = false
            }
            return true
        }
    }
    return false
}

/**
 * Evaluate predicate using atomics set to given values.
 */
private fun <T> eval(p: Predicate<T>, v: BitSet, atomics: List<Predicate<T>>): Boolean {
    var result = false
    p.accept(object : PredicateVisitor<T>() {
        override fun visitNot(predicate: NotPredicate<T>) {
            result = !eval(predicate.operand, v, atomics)
        }

        override fun visitAndPredicate(predicate: AndPredicate<T>) {
            result = predicate.operands.all { eval(it, v, atomics) }
        }

        override fun visitOrPredicate(predicate: OrPredicate<T>) {
            result = predicate.operands.any { eval(it, v, atomics) }
        }

        override fun visit(predicate: Predicate<T>) {
            result = v[atomics.indexOf(predicate)]
        }
    })
    return result
}