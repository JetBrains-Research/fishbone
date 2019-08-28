package org.jetbrains.bio.util.chianti.parser

import com.epam.parso.impl.SasFileReaderImpl
import org.jetbrains.bio.predicate.OverlapSamplePredicate
import org.jetbrains.bio.rule.Rule
import org.jetbrains.bio.rule.validation.RuleImprovementCheck
import org.jetbrains.bio.predicate.PredicatesConstructor
import org.jetbrains.bio.util.chianti.codebook.Codebook
import org.jetbrains.bio.util.chianti.codebook.CodebookToPredicatesTransformer
import org.jetbrains.bio.util.chianti.variable.CombinedFeature
import org.jetbrains.bio.util.chianti.variable.Reference
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaboratoryDataParserTest {
    private val referenceFilename: String = "/path/to/reference"
    private val sexReferenceFilename: String = "/path/to/sex_reference"
    private val dataFilename: String = "/path/to/data"
    private val codebookFilename: String = "/path/to/codebook"
    private val outputDir: String = "/path/to/output"
    private val sex = 2L

    private val databasePath = "$outputDir/target/database.txt"
    private val targetPath = "$outputDir/target"
    private val specialFiles = listOf(databasePath, outputDir, targetPath)

    private val processor = LaboratoryDataParser(referenceFilename, sexReferenceFilename, dataFilename)
    private val references = processor.readReferences()
    private val predicatesMap = {
        CodebookToPredicatesTransformer(
                Codebook(codebookFilename, processor.irrelevantFeatures, processor.redundantFeatures),
                references.map { ref -> Reference(ref[0], ref[1].toDouble(), ref[3].toDouble()) }
        ).predicates
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
                .filter { processor.ageInRange(it[5]) }
                .filter { it[2].toString().toLong() == sex }
                .map { it[0].toString().toInt() }

        val database = Paths.get(databasePath).toFile().useLines { outer -> outer.map { it.toInt() }.toList() }

        assertEquals(dataCodes,
                database,
                "Database must contain code98 fields for all patients in predefined age range: ${dataCodes.subtract(database)}"
        )
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
                            && label !in processor.sexDependentFeatures
                }
                .filter { predicateName ->
                    val idx = columnsByIdx.keys.find { idx ->
                        val column = columnsByIdx.getValue(idx)
                        DataParser.predicateNameIsApplicableForColumn(column, predicateName)
                    }!!
                    processor.isEnoughPresentedFeature(data, 5, idx)
                }

        val outputFiles = File(outputDir).walkTopDown().filter { it.absolutePath !in specialFiles }
        val predicateFileNames = outputFiles
                .map { it.nameWithoutExtension }
                .toList()

        // Check irrelevant and redundant features
        processor.irrelevantFeatures.forEach { p ->
            assertFalse(predicateFileNames.contains(p), "No irrelevant features must be used: $p")
        }
        processor.redundantFeatures.forEach { p ->
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
                .forEach { ps -> assertTrue("Combined feature not found: $ps") { ps.any { predicateFileNames.contains(it) } } }

        CombinedFeature.values()
                .forEach {
                    it.labels.forEach { label ->
                        assertFalse { predicateFileNames.contains(label) }
                    }
                }

        predicates
                .filter { !it.contains("SEX_is_$sex") }
                .forEach { p ->
                    assertTrue(
                            predicateFileNames.contains(p),
                            "Each predicate must be saved to corresponding file: $p"
                    )
                }
    }

    @Test
    fun testNA() {
        val outputFiles = File(outputDir).walkTopDown().filter { it.absolutePath !in specialFiles }
        val predicates = PredicatesConstructor.createOverlapSamplePredicates(outputFiles.map { it.absolutePath }.toList())
        predicates
                .filter { p ->
                    !CombinedFeature.values().any { f -> DataParser.predicateNameIsApplicableForColumn(f.title, p.name()) }
                }
                .forEach { p ->
                    val idx = columnsByIdx.keys.find { idx ->
                        val column = columnsByIdx.getValue(idx)
                        DataParser.predicateNameIsApplicableForColumn(column, p.name())
                    }!!
                    // sex-dependent features should be checked later
                    if (columnsByIdx.getValue(idx) !in processor.sexDependentFeatures) {
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
        val target = PredicatesConstructor.createOverlapSamplePredicates(listOf(targetPath))[0]
        val database = Paths.get(databasePath).toFile().useLines { outer -> outer.map { it.toInt() }.toList() }

        val cortdhFile = File(outputDir).walkTopDown()
                .filter { it.absolutePath !in specialFiles }
                .find { it.nameWithoutExtension == "low_CORTDH" }!!.absolutePath
        val cordthNoNa = PredicatesConstructor.createOverlapSamplePredicates(listOf(cortdhFile))[0]
        val ruleNoNa = Rule(cordthNoNa.not(), target, database)
        val pNoNa = RuleImprovementCheck.testRuleProductivity(ruleNoNa, database)

        val naCortdh = data.filter { it[173] == null }.map { it[0].toString().toInt() }
        val cortdhWithNa = OverlapSamplePredicate(
                cordthNoNa.name(), cordthNoNa.samples, cordthNoNa.notSamples + naCortdh
        )
        val ruleNa = Rule(cortdhWithNa.not(), target, database)
        val pNa = RuleImprovementCheck.testRuleProductivity(ruleNa, database)

        assertTrue(pNoNa < pNa)
    }

    @Test
    fun testSexDependentFeatures() {
        val outputFiles = File(outputDir).walkTopDown().filter { it.absolutePath !in specialFiles }
        val predicates = PredicatesConstructor.createOverlapSamplePredicates(outputFiles.map { it.absolutePath }.toList())
        val sexName = if (sex == 1L) "male" else "female"
        predicates.forEach { p ->
            val isSexDependent = processor.sexDependentFeatures.any { DataParser.predicateNameIsApplicableForColumn(it, p.name()) }
            if (isSexDependent) {
                val label = p.name()
                        .replace("below_ref_", "")
                        .replace("above_ref_", "")
                        .replace("inside_ref_", "")
                        .replace("low_", "")
                        .replace("high_", "")
                        .replace("normal_", "")
                val isReferenceBasedFeature = "${label}_$sexName" in references.map { it[0] }
                if (isReferenceBasedFeature) {
                    val idx = columnsByIdx.keys.find { idx ->
                        DataParser.predicateNameIsApplicableForColumn(columnsByIdx.getValue(idx), label)
                    }!!
                    p.samples.forEach { code ->
                        val patient = data.find { it[0].toString().toInt() == code }!!
                        val ref = getReference(patient, p)
                        assertTrue { ref(patient[idx].toString()) }
                    }
                    p.notSamples.forEach { code ->
                        val patient = data.find { it[0].toString().toInt() == code }!!
                        val ref = getReference(patient, p)
                        assertFalse { ref(patient[idx].toString()) }
                    }
                    val naSamples = data
                            .filter { it[idx] == null }
                            .map { it[0].toString().toInt() }
                    naSamples.forEach { assertFalse { p.test(it) } }
                    naSamples.forEach { assertFalse { p.not().test(it) } }
                }
                data.filter { it[0].toString().toInt() in p.samples }
                        .forEach { assertEquals(sex, it[2].toString().toLong(), "Invalid sex for patient ${it[0]}") }
            }
        }

    }

    private fun getReference(patient: Array<Any>, p: OverlapSamplePredicate): (Any) -> Boolean {
        return if (patient[2].toString().toInt() == 1) {
            predicatesMap.getValue("${p.name()}_male")
        } else {
            predicatesMap.getValue("${p.name()}_female")
        }
    }
}
