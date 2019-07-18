package org.jetbrains.bio.util.chianti.model

abstract class Variable(val name: String, val meaning: String, val isSexDependent: Boolean = SexDependentFeature.labels().contains(name))