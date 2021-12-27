package org.jetbrains.bio.fishbone.rule.log

import junit.framework.TestCase.assertEquals
import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.miner.RangePredicate
import org.jetbrains.bio.fishbone.miner.named
import org.jetbrains.bio.fishbone.predicate.FalsePredicate
import org.jetbrains.bio.fishbone.rule.Rule
import org.junit.Test
import java.awt.Color

/**
 * @author Oleg Shpynov
 * @date 16/12/2016
 */

class RulesLoggerTest {

    @Test
    fun testGetJson() {
        val predicates = 0.until(2).map { RangePredicate(it * 10, (it + 1) * 10) }
        val database = 0.until(50).toList()
        val logger = RulesLogger(null)
        FishboneMiner.mine("foo", database, listOf(predicates to RangePredicate(0, 20)),
            { logger.log("id", it) }, maxComplexity = 2, topPerComplexity = 1, buildHeatmapAndUpset = true
        )
        val actualMap = logger.prepareJson({ Color.WHITE }, "conviction")
        val expectedMap = RulesLogger.GSON.fromJson("""
    {
      "records": [
        {
          "id": "id",
          "condition": "[0;10) OR [10;20)",
          "target": "[0;20)",
          "database_count": 50,
          "condition_count": 20,
          "target_count": 20,
          "intersection_count": 20,
          "support": 0.4,
          "confidence": 1.0,
          "correlation": 1.0,
          "lift": 2.5,
          "conviction": 12.0,
          "loe": 1.4274698120945677,
          "complexity": 2,
          "node": "[0;10)",
          "parent_node": "[10;20)",
          "parent_condition": "[10;20)",
          "aux": {
            "rule": {
              "names": [
                "[0;10)",
                "[10;20)",
                "[0;20)"
              ],
              "combinations": [
                30,
                0,
                0,
                0,
                0,
                10,
                10,
                0
              ]
            }
          },
          "operator": "or"
        },
        {
          "id": "id",
          "condition": "[10;20)",
          "target": "[0;20)",
          "database_count": 50,
          "condition_count": 10,
          "target_count": 20,
          "intersection_count": 10,
          "support": 0.2,
          "confidence": 1.0,
          "correlation": 0.6123724356957945,
          "lift": 2.5,
          "conviction": 6.0,
          "loe": 1.2007703985251723,
          "complexity": 1,
          "node": "[10;20)",
          "aux": {
            "rule": {
              "names": [
                "[10;20)",
                "[0;20)"
              ],
              "combinations": [
                30,
                0,
                10,
                10
              ]
            }
          },
          "operator": "none"
        },
        {
          "id": "id",
          "condition": "TRUE",
          "target": "[0;20)",
          "database_count": 50,
          "condition_count": 50,
          "target_count": 20,
          "intersection_count": 20,
          "support": 1.0,
          "confidence": 0.4,
          "correlation": 0.0,
          "lift": 1.0,
          "conviction": 0.967741935483871,
          "loe": 0.20827504596683347,
          "complexity": 1,
          "node": "TRUE",
          "aux": {
            "heatmap": {
              "tableData": [
                {
                  "key": "[0;20)",
                  "values": [
                    {
                      "key": "[0;20)",
                      "value": 1.0
                    },
                    {
                      "key": "[10;20)",
                      "value": 0.6123724356957945
                    }
                  ]
                },
                {
                  "key": "[10;20)",
                  "values": [
                    {
                      "key": "[0;20)",
                      "value": 0.6123724356957945
                    },
                    {
                      "key": "[10;20)",
                      "value": 1.0
                    }
                  ]
                }
              ],
              "rootData": {
                "totalLength": 1.5142135623730952,
                "children": [
                  {
                    "length": 1.4142135623730951,
                    "key": "[0;20)"
                  },
                  {
                    "length": 1.4142135623730951,
                    "key": "[10;20)"
                  }
                ]
              }
            },
            "upset": {
              "names": [
                "[0;20)",
                "[10;20)"
              ],
              "data": [
                {
                  "id": [
                    0
                  ],
                  "n": 20
                },
                {
                  "id": [
                    0,
                    1
                  ],
                  "n": 10
                },
                {
                  "id": [
                    1
                  ],
                  "n": 10
                }
              ]
            }
          },
          "operator": "none"
        }
      ],
      "palette": {
        "TRUE": "#ffffff",
        "[0;10)": "#ffffff",
        "[0;20)": "#ffffff",
        "[10;20)": "#ffffff"
      },
      "criterion": "conviction"
    }""", Map::class.java)
        val expected = RulesLogger.GSON.toJson(expectedMap)
        val actual = RulesLogger.GSON.toJson(actualMap)
        assertEquals(expected.replace(".0", ""), actual.replace(".0", ""))
    }


    @Test
    fun testRuleRecordCSV() {
        val rule = Rule(FalsePredicate<Any>().named("foo"), FalsePredicate<Any>().named("bar"), 10, 8, 9, 7)
        assertEquals(
            listOf(
                "id", "foo", "bar", 10, 8, 9, 7, 0.8, 0.875,
                -0.16666666666666666, 0.9722222222222222, 0.4, 0.22427683792970576, 1
            ),
            RuleRecord.fromRule(rule, "id").toCSV()
        )
    }

    @Test
    fun testRuleRecordMap() {
        val rule = Rule(FalsePredicate<Any>().named("foo"), FalsePredicate<Any>().named("bar"), 10, 8, 9, 7)
        assertEquals(
            "{id=id, condition=foo, target=bar, " +
                    "database_count=10, condition_count=8, target_count=9, intersection_count=7, " +
                    "support=0.8, confidence=0.875, " +
                    "correlation=-0.16666666666666666, lift=0.9722222222222222, conviction=0.4, loe=0.22427683792970576, " +
                    "complexity=1}",
            RuleRecord.fromRule(rule, "id").toMap().toString()
        )
    }

}
