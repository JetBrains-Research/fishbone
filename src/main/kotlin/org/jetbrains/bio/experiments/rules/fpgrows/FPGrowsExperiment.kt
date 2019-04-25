package org.jetbrains.bio.experiments.rules.fpgrows

import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.experiments.rules.Experiment
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.containers.LocationsMergingList
import org.jetbrains.bio.util.BedFileHelper
import org.jetbrains.bio.util.div
import smile.association.ARM
import smile.association.AssociationRule
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FPGrowsExperiment : Experiment {
    private val genomeQuery = GenomeQuery("mm10")
    private val defaultOutputFolder = "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_output"
    private val associationRuleComparator = Comparator
        .comparingDouble(AssociationRule::support)
        .thenComparing(AssociationRule::confidence)!!

    // TODO: parameters
    val topN = 10
    val minConfidence = 0.1

    class PredicateInfo(val id: Int, val name: String, val satisfactionOnIds: BitSet)

    override fun run(mineRulesRequest: MineRulesRequest): String {
        val databasePath = Paths.get(mineRulesRequest.database)
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss"))
        val rulesResults = defaultOutputFolder / "rules_$timestamp.txt"

        val database = LocationsMergingList.load(genomeQuery, databasePath)
        val items = database.toList()

        val sourcePredicates = getPredicatesInfoOverDatabase(mineRulesRequest.sources, items, "1")
        val sourceIdsToNames = sourcePredicates.map { it.id to it.name }.toMap()
        val targetPredicates = getPredicatesInfoOverDatabase(mineRulesRequest.targets, items, "2")
        val targetIdsToNames = targetPredicates.map { it.id to it.name }.toMap()

        val itemsets = items.withIndex().map { (idx, _) ->
            val satisfiedSources = getSatisfiedPredicateIds(sourcePredicates, idx)
            val satisfiedTargets = getSatisfiedPredicateIds(targetPredicates, idx)
            (satisfiedSources + satisfiedTargets).toIntArray()
        }
        val arm = ARM(itemsets.toTypedArray(), 1)
        val rules = arm.learn(minConfidence)
        val result = rules
            .sortedWith(compareByDescending(associationRuleComparator) { v -> v })
            .filter { rule ->
                rule.antecedent.all { ant -> sourcePredicates.map { it.id }.contains(ant) } &&
                        rule.consequent.all { ant -> targetPredicates.map { it.id }.contains(ant) }
            }
            .take(topN)
            .map { NamedAssociationRule(it, sourceIdsToNames, targetIdsToNames) }

        val file = rulesResults.toFile()
        file.createNewFile()
        file.printWriter().use { out -> result.forEach { out.println(it) } }

        return rulesResults.toString()
    }

    private fun getPredicatesInfoOverDatabase(paths: List<String>, database: List<Location>, suffix: String)
            : List<PredicateInfo> {
        val predicates = BedFileHelper.createPredicates(genomeQuery, paths)
        val names = paths.map { getPredicateNameFromFilenpath(it) }
        return predicates.withIndex().map { (idx, predicate) ->
            val id = (idx.toString() + suffix).toInt()
            PredicateInfo(id, names[idx], predicate.test(database))
        }
    }

    private fun getSatisfiedPredicateIds(predicates: List<PredicateInfo>, itemIdx: Int) =
        predicates.filter { it.satisfactionOnIds[itemIdx] }.map { it.id }

    private fun getPredicateNameFromFilenpath(path: String): String {
        val filename = path.split("/").last()
        val filenameParts = filename.split(".")
        return filenameParts[filenameParts.size - 2]
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val sources = listOf(
                "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_experiments/allTfsToIrf4/source_Batf.bed",
                "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_experiments/allTfsToIrf4/source_Maf.bed",
                "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_experiments/allTfsToIrf4/source_RORC.bed",
                "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_experiments/allTfsToIrf4/source_Stat3.bed"
            )
            val targets = listOf(
                "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_experiments/allTfsToIrf4/target_IRF4.bed"
            )
            val database =
                "/home/nlukashina/education/bioinf/spring/fishbone_materials/ciofani_experiments/allTfsToIrf4/allTfsToIrf4_database.bed"
            println(FPGrowsExperiment().run(MineRulesRequest("fpgrows", sources, targets, database)))
        }
    }
}