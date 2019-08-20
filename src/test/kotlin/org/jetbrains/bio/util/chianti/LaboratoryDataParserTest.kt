package org.jetbrains.bio.util.chianti

import com.epam.parso.impl.SasFileReaderImpl
import org.apache.commons.math3.stat.inference.ChiSquareTest
import org.jetbrains.bio.predicates.OverlapSamplePredicate
import org.jetbrains.bio.rules.Rule
import org.jetbrains.bio.rules.validation.ChiSquaredCheck
import org.jetbrains.bio.rules.validation.RuleSignificanceCheck
import org.jetbrains.bio.util.PredicatesHelper
import org.jetbrains.bio.util.chianti.model.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// TODO: set your proper paths
class LaboratoryDataParserTest {
    private val referenceFilename: String = "rbl_filtered.csv"
    private val sexReferenceFilename: String = "ref_sd_bl.csv"
    private val dataFilename: String = "labo_raw.sas7bdat"
    private val codebookFilename: String = "labo_raw_fixed.xlsx"
    private val outputDir: String = "exp13_no_na_male"

    private val databasePath = "$outputDir/database.txt"
    private val specialFiles = listOf(databasePath, outputDir, "$outputDir/target")

    private val processor = LaboratoryDataParser(referenceFilename, sexReferenceFilename, dataFilename)
    private val references = processor.readReferences()
    private val predicatesMap = {
        val withReferencePredicates = ReferencesToPredicatesTransformer(references).predicates
        val codebook = CodebookReader(codebookFilename).readCodebook()
        val noReferencePredicates = LaboratoryDataParser.noReferencePredicates(codebook, references)
        noReferencePredicates + withReferencePredicates
    }()
    private val reader = SasFileReaderImpl(FileInputStream(dataFilename))
    private val data = reader.readAll()
    private val columnsByIdx = reader.columns
            .map { Regex("""^[XYZQC]_""").replaceFirst(it.name, "") }
            .withIndex()
            .map { it.index to it.value }.toMap()

    @Test
    fun testDatabase() {
        val dataCodes = SasFileReaderImpl(FileInputStream(dataFilename)).readAll()
                .filter {
                    val age = it[5].toString().toDouble()
                    age in LaboratoryDataParser.oldAgeRange || age < 40
                }
                .map { it[0].toString().toInt() }

        val database = Paths.get(databasePath).toFile().useLines { outer -> outer.map { it.toInt() }.toList() }

        assertEquals(dataCodes, database, "Database must contain code98 fields for all patients in predefined age range")
    }

    @Test
    fun testPredicates() {
        val predicates = predicatesMap
                .map { it.key }
                .filter { p ->
                    val label = p
                            .replace("_female", "")
                            .replace("_male", "")
                            .replace("below_ref_", "")
                            .replace("above_ref_", "")
                            .replace("inside_ref_", "")
                            .replace("low_", "")
                            .replace("high_", "")
                            .replace("normal_", "")
                    CombinedFeature.getByLabel(label) == null && CombinedFeature.getByTitle(label) == null
                }
                .filter { p ->
                    val predicateName = p.replace("_female", "").replace("_male", "")
                    if (!predicateName.contains("VIT_E")) {
                        val idx = columnsByIdx.keys.find { idx ->
                            val column = columnsByIdx.getValue(idx)
                            processor.predicateNameIsApplicableForColumn(column, predicateName)
                        }!!
                        processor.isEnoughPresentedFeature(data, 5, idx)
                    } else {
                        true
                    }
                }

        val outputFiles = File(outputDir).walkTopDown().filter { it.absolutePath !in specialFiles }
        val predicateFileNames = outputFiles
                .map { it.nameWithoutExtension }
                .toList()

        // Check irrelevant and redundant features
        IrrelevantFeature.labels().forEach { p ->
            assertFalse(predicateFileNames.contains(p), "No irrelevant features must be used: $p")
        }
        RedundantFeature.labels().forEach { p ->
            assertFalse(predicateFileNames.contains(p), "No redundant features must be used: $p")
        }

        // Check combined features
        val refs = references.map { it[0] }
        CombinedFeature.values()
                .map { it.title }
                .map {
                    val isReferenceBasedFeature = "${it}_male" in refs || it in refs
                    if (isReferenceBasedFeature) {
                        listOf("above_ref_$it", "below_ref_$it", "inside_ref_$it")
                    } else {
                        listOf("high_$it", "low_$it", "normal_$it")
                    }
                }
                .forEach { ps -> assertTrue { ps.any { predicateFileNames.contains(it) } } }
        CombinedFeature.values()
                .forEach {
                    it.labels.forEach { label ->
                        assertFalse { predicateFileNames.contains(label) }
                    }
                }

        predicates
                .filter { !it.contains("SEX_is_2") }
                .filter { !it.contains("GAMTOC") && !it.contains("ALFTOC") }
                .forEach { p ->
                    val predicateName = p.replace("_female", "").replace("_male", "")
                    assertTrue(
                            predicateFileNames.contains(predicateName),
                            "Each predicate must be saved to corresponding file: $predicateName"
                    )
                }
    }

    @Test
    fun testNA() {
        val outputFiles = File(outputDir).walkTopDown().filter { it.absolutePath !in specialFiles }
        val predicates = PredicatesHelper.createOverlapSamplePredicates(outputFiles.map { it.absolutePath }.toList())
        predicates
                .filter { p ->
                    !CombinedFeature.values().any { f -> processor.predicateNameIsApplicableForColumn(f.title, p.name()) }
                }
                .forEach { p ->
                    val idx = columnsByIdx.keys.find { idx ->
                        val column = columnsByIdx.getValue(idx)
                        processor.predicateNameIsApplicableForColumn(column, p.name())
                    }!!
                    // sex-dependent features should be checked later
                    if (columnsByIdx.getValue(idx) !in SexDependentFeature.labels()) {
                        p.samples.forEach { assertTrue { p.test(it) } }
                        p.notSamples.forEach { assertTrue { p.not().test(it) } }
                        p.notSamples.forEach { assertFalse { p.test(it) } }
                        val naSamples = data
                                .filter { it[idx] == null }
                                .map { it[0].toString().toInt() }
                        naSamples.forEach { assertFalse { p.test(it) } }
                        naSamples.forEach { assertFalse { p.not().test(it) } }
                    }
                }
    }

    @Test
    fun testNAPvalue() {
        val target = PredicatesHelper.createOverlapSamplePredicates(listOf("AGEL_is_old"))[0]
        val database = Paths.get("database.txt").toFile().useLines { outer -> outer.map { it.toInt() }.toList() }

        val cortdhFile = File(outputDir).walkTopDown()
                .filter { it.absolutePath !in specialFiles }
                .find { it.nameWithoutExtension == "low_CORTDH" }!!.absolutePath
        val cordthNoNa = PredicatesHelper.createOverlapSamplePredicates(listOf(cortdhFile))[0]
        val ruleNoNa = Rule(cordthNoNa.not(), target, database)
        val pNoNa = RuleSignificanceCheck.test(ruleNoNa, database)

        val naCortdh = data.filter { it[173] == null }.map { it[0].toString().toInt() }
        val cortdhWithNa = OverlapSamplePredicate(
                cordthNoNa.name(), cordthNoNa.samples, cordthNoNa.notSamples + naCortdh
        )
        val ruleNa = Rule(cortdhWithNa.not(), target, database)
        val pNa = RuleSignificanceCheck.test(ruleNa, database)

        assertTrue(pNoNa < pNa)
    }

    @Test
    fun testSexDependentFeatures() {
        val outputFiles = File(outputDir).walkTopDown().filter { it.absolutePath !in specialFiles }
        val predicates = PredicatesHelper.createOverlapSamplePredicates(outputFiles.map { it.absolutePath }.toList())
        predicates.forEach { p ->
            val isSexDependent = SexDependentFeature.labels().any { it.contains(p.name()) }
            if (isSexDependent) {
                val isReferenceBasedFeature = "${p.name()}_male" in references.map { it[0] }
                if (isReferenceBasedFeature) {
                    val idx = columnsByIdx.keys.find { idx ->
                        processor.predicateNameIsApplicableForColumn(columnsByIdx.getValue(idx), p.name())
                    }!!
                    p.samples.forEach { code ->
                        val patient = data.find { it[0] == code }!!
                        val ref = getReference(patient, p)
                        assertTrue { ref(patient[idx]) }
                    }
                    p.notSamples.forEach { code ->
                        val patient = data.find { it[0] == code }!!
                        val ref = getReference(patient, p)
                        assertFalse { ref(patient[idx]) }
                    }
                    val naSamples = data
                            .filter { it[idx] == null }
                            .map { it[0].toString().toInt() }
                    naSamples.forEach { assertFalse { p.test(it) } }
                    naSamples.forEach { assertFalse { p.not().test(it) } }
                } else {

                }
            }
        }

    }

    private fun getReference(patient: Array<Any>, p: OverlapSamplePredicate): (Any) -> Boolean {
        return if (patient[4].toString().toInt() == 1) {
            predicatesMap.getValue("${p.name()}_male")
        } else {
            predicatesMap.getValue("${p.name()}_female")
        }
    }
}
