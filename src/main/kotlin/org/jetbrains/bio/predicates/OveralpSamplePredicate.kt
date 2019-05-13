package org.jetbrains.bio.predicates

class OveralpSamplePredicate(val name: String, val samples: List<Int>) : Predicate<Int>() {
    override fun test(item: Int): Boolean {
        return samples.contains(item)
    }

    override fun name(): String {
        return name
    }
}