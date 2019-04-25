package org.jetbrains.bio.experiments.rules.fpgrows

import smile.association.AssociationRule

class NamedAssociationRule(
    rule: AssociationRule,
    sourceIdsToNames: Map<Int, String>,
    targetIdsToNames: Map<Int, String>
) : AssociationRule(rule.antecedent, rule.consequent, rule.support, rule.confidence) {

    val namedAntecedent: List<String> = rule.antecedent.map { sourceIdsToNames.getValue(it) }
    val namedConsequent: List<String> = rule.consequent.map { targetIdsToNames.getValue(it) }

    override fun toString(): String {
        val roundedSupport = "%.3f".format(support)
        val roundedConfidence = "%.3f".format(confidence)
        return "$namedAntecedent ==> $namedConsequent, \t support = $roundedSupport, \t confidence = $roundedConfidence"
    }
}