package org.jetbrains.bio.fishbone.predicate

open class OverlapSamplePredicate(
    private val name: String, val samples: List<Int>, val notSamples: List<Int>
) : Predicate<Int>() {
    override fun test(item: Int): Boolean {
        return samples.contains(item)
    }

    override fun name(): String {
        return name
    }

    override fun not(): Predicate<Int> {
        return OverlapSamplePredicate("${PredicateParser.NOT.token} ${name()}", notSamples, samples)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OverlapSamplePredicate) return false

        if (name != other.name) return false
        if (samples != other.samples) return false
        if (notSamples != other.notSamples) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + samples.hashCode()
        result = 31 * result + notSamples.hashCode()
        return result
    }
}