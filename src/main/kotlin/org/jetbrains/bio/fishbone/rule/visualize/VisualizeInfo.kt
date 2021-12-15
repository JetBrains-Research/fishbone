package org.jetbrains.bio.fishbone.rule.visualize


/**
 * Info for visualization purposes.
 */
interface VisualizeInfo

data class RuleVisualizeInfo(val rule: Combinations) : VisualizeInfo

data class TargetVisualizeInfo(val heatmap: Heatmap, val upset: Upset) : VisualizeInfo