package org.jetbrains.bio.rules.fpgrowth

import smile.association.AssociationRule

class NamedAssociationRule(
    rule: AssociationRule,
    predicatesIdsToNames: Map<Int, String>
) : AssociationRule(rule.antecedent, rule.consequent, rule.support, rule.confidence) {

    private val namedAntecedent: List<String> = rule.antecedent.map { predicatesIdsToNames.getValue(it) }
    private val namedConsequent: List<String> = rule.consequent.map { predicatesIdsToNames.getValue(it) }

    override fun toString(): String {
        val roundedSupport = "%.3f".format(support)
        val roundedConfidence = "%.3f".format(confidence)
        return "$namedAntecedent ==> $namedConsequent, \t support = $roundedSupport, \t confidence = $roundedConfidence"
    }
}