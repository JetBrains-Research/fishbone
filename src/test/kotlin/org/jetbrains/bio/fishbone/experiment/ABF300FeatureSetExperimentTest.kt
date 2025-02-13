package org.jetbrains.bio.fishbone.experiment

import org.jetbrains.bio.fishbone.api.*
import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.predicate.OverlapSamplePredicate
import org.jetbrains.bio.fishbone.predicate.PredicatesConstructor
import org.jetbrains.bio.fishbone.rule.Rule
import org.jetbrains.bio.fishbone.experiment.FarmExperiment
import org.junit.AfterClass
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.toPath
import kotlin.test.assertEquals

class ABF300FeatureSetExperimentTest {

    companion object {
        // below we define two real predicates from ABF300 dataset
        // first predicate is "donor is MALE", negation is "donor is FEMALE"
        // second predicate is "donors Creatinine > 1", negation is "donors Creatinine <= 1"
        // the values of confidence, coverage, lift and conviction
        // were calculated using `arules` R package
        //
        // rules                               support confidence  coverage     lift count conviction
        // 1 {Creatinine_gt_1} => {Gender_M} 0.4733333  0.9861111 0.4800000 1.183333    71  12.000000
        // 2 {Gender_M} => {Creatinine_gt_1} 0.4733333  0.5680000 0.8333333 1.183333    71   1.203704
        //
        // Corresponding files for these predicates (Gender_M and Creatinine_gt_1) are also present in the test resources
        // As well as the database.txt
        //
        // Contingency table for the predicates
        //        Creatinine_gt_1
        // Gender_M FALSE TRUE
        //    FALSE    24    1
        //    TRUE     54   71
        // `chisq.test(contingency_table)$p.value`
        //  4.133496e-06
        //  Must be a productive rule
        //
        //
        //
        // fisher.test(contingency_table)
        // Fisher's Exact Test for Count Data
        // data:  contingency_table
        // p-value = 3.322e-07
        // alternative hypothesis: true odds ratio is not equal to 1
        // 95 percent confidence interval:
        // 4.759189 1306.929589
        // sample estimates:
        // odds ratio
        // 31.03686

        private val tempDir = createTempDir("temp-${System.currentTimeMillis()}")
        val experiment = FeaturesSetExperiment(tempDir.absolutePath)
        val request = MineRulesRequest(
            experiment = ExperimentType.FEATURE_SET,
            genome = "",
            predicates = listOf(this::class.java.classLoader.getResource("abf300_creatinine_test/Gender_M").toURI().toPath().toString()),
            targets = listOf(this::class.java.classLoader.getResource("abf300_creatinine_test/Creatinine_gt_1").toURI().toPath().toString()),
            database = this::class.java.classLoader.getResource("abf300_creatinine_test/database.txt").toURI().toPath().toString(),
            miners = HashSet(listOf(MiningAlgorithm.FISHBONE)),
            criterion = "conviction",
            significanceLevel = 0.05,
            runName = "test-abf300",
            settings = ExperimentSettings(),
        )


        val database = (1..150).toList()
        val isMale = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125)
        val isFemale = listOf(126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150)
        val creatinineGt1 = listOf(4, 5, 6, 8, 9, 13, 14, 17, 18, 21, 23, 24, 25, 26, 27, 30, 32, 33, 34, 36, 39, 44, 46, 48, 49, 56, 57, 58, 60, 61, 62, 64, 65, 66, 67, 68, 69, 72, 74, 78, 80, 82, 84, 87, 88, 89, 92, 93, 95, 96, 97, 98, 99, 100, 102, 103, 104, 105, 106, 107, 108, 109, 110, 116, 117, 119, 120, 121, 122, 124, 125, 149)
        val creatinineLet1 = listOf(1, 2, 3, 7, 10, 11, 12, 15, 16, 19, 20, 22, 28, 29, 31, 35, 37, 38, 40, 41, 42, 43, 45, 47, 50, 51, 52, 53, 54, 55, 59, 63, 70, 71, 73, 75, 76, 77, 79, 81, 83, 85, 86, 90, 91, 94, 101, 111, 112, 113, 114, 115, 118, 123, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 150)

        val malePredicate = OverlapSamplePredicate("is_male", isMale, isFemale)
        val creatinineHighPredicate = OverlapSamplePredicate("high_creatinine", creatinineGt1, creatinineLet1)
        val creatinineLowPredicate = OverlapSamplePredicate("low_creatinine", creatinineLet1, creatinineGt1)

        @JvmStatic
        @AfterClass
        fun cleanup(): Unit {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testABF300Mining() {
        var rules = FishboneMiner.mine(
            listOf(malePredicate),
            creatinineHighPredicate,
            database,
            maxComplexity = 10,
            topPerComplexity = FishboneMiner.TOP_PER_COMPLEXITY,
            function = Rule<Int>::conviction,
            functionDelta = FishboneMiner.FUNCTION_DELTA,
            klDelta = FishboneMiner.KL_DELTA

        )
        assertEquals(3, rules.size)

        // best rule
        var rule = rules.first()
        assertEquals("is_male => high_creatinine", rule.rule.toString())
        assertEquals(1.2037, rule.rule.conviction, 1e-3)

        rule = rules[1]
        assertEquals("NOT is_male => high_creatinine", rule.rule.toString())

        rule = rules[2]
        assertEquals("TRUE => high_creatinine", rule.rule.toString())


        rules = FishboneMiner.mine(
            listOf(malePredicate),
            creatinineLowPredicate,
            database,
            maxComplexity = 10,
            topPerComplexity = FishboneMiner.TOP_PER_COMPLEXITY,
            function = Rule<Int>::conviction,
            functionDelta = FishboneMiner.FUNCTION_DELTA,
            klDelta = FishboneMiner.KL_DELTA

        )
        assertEquals(3, rules.size)

        // best rule
        rule = rules.first()
        assertEquals("NOT is_male => low_creatinine", rule.rule.toString())
        assertEquals(12.000, rule.rule.conviction, 1e-3)

        rule = rules[1]
        assertEquals("is_male => low_creatinine", rule.rule.toString())

        rule = rules[2]
        assertEquals("TRUE => low_creatinine", rule.rule.toString())


    }

    @Test
    fun testRuleProductivity () {
        val rules = FishboneMiner.mine(
            listOf(malePredicate),
            creatinineHighPredicate,
            database,
            maxComplexity = 10,
            topPerComplexity = FishboneMiner.TOP_PER_COMPLEXITY,
            function = Rule<Int>::conviction,
            functionDelta = FishboneMiner.FUNCTION_DELTA,
            klDelta = FishboneMiner.KL_DELTA

        )

        val productiveRules = experiment.getProductiveRules(
            MiningAlgorithm.FISHBONE,
            rules,
            alpha = 0.05,
            db = database,
            adjust = false
        )

        assertEquals(3, productiveRules.size)
        // best rule
        val rule = productiveRules.first()
        assertEquals("is_male => high_creatinine", rule.rule.toString())
        assertEquals(1.2037, rule.rule.conviction, 1e-3)
    }

    @Test
    fun testPredicatesConstructor () {
        // let's check if Predicates Constructor properly build the predicates
        val databasePath = Paths.get(request.database)
        val database = databasePath.toFile().useLines { outer -> outer.map { it.toInt() }.toList() }
        val predicates = PredicatesConstructor.createOverlapSamplePredicates(request.predicates)
        val targets = PredicatesConstructor.createOverlapSamplePredicates(request.targets)

        var rules = FishboneMiner.mine(
            predicates,
            targets[0],
            database,
            maxComplexity = 10,
            topPerComplexity = FishboneMiner.TOP_PER_COMPLEXITY,
            function = Rule<Int>::conviction,
            functionDelta = FishboneMiner.FUNCTION_DELTA,
            klDelta = FishboneMiner.KL_DELTA

        )
        assertEquals(3, rules.size)

        // best rule
        var rule = rules.first()
        assertEquals("Gender_M => Creatinine_gt_1", rule.rule.toString())
        assertEquals(1.2037, rule.rule.conviction, 1e-3)

    }

    @Test
    fun testABF300RequestProcessing () {
        val result = experiment.run(request)
        val what = result[MiningAlgorithm.FISHBONE]
        assertEquals(1, result.size)
    }

}