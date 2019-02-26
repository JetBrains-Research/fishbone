package org.jetbrains.bio.predicates

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
