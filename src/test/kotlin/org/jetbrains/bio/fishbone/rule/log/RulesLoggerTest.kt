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
        val predicates = 0.until(2).map { RangePredicate(0, (it + 1) * 10) }
        val database = 0.until(50).toList()
        val logger = RulesLogger(null)
        FishboneMiner.mine(
            "foo", database, listOf(predicates to RangePredicate(10, 20)),
            { logger.log("id", it) }, maxComplexity = 2, topPerComplexity = 1, buildHeatmapAndUpset = true
        )
        val actualMap = logger.prepareJson("conviction") { Color.WHITE }
        val expectedMap = RulesLogger.GSON.fromJson(
            """{
  "records": [
    {
      "id": "id",
      "condition": "NOT [0;10) AND [0;20)",
      "target": "[10;20)",
      "database_count": 50,
      "condition_count": 10,
      "target_count": 10,
      "intersection_count": 10,
      "support": 0.2,
      "confidence": 1,
      "correlation": 1,
      "lift": 5,
      "conviction": 8,
      "loe": 1.151802008640984,
      "complexity": 2,
      "node": "NOT [0;10)",
      "parent_node": "[0;20)",
      "parent_condition": "[0;20)",
      "aux": {
        "rule": {
          "names": [
            "NOT [0;10)",
            "[0;20)",
            "[10;20)"
          ],
          "combinations": [
            0,
            30,
            10,
            0,
            0,
            0,
            0,
            10
          ]
        }
      },
      "operator": "and"
    },
    {
      "id": "id",
      "condition": "[0;20)",
      "target": "[10;20)",
      "database_count": 50,
      "condition_count": 20,
      "target_count": 10,
      "intersection_count": 10,
      "support": 0.4,
      "confidence": 0.5,
      "correlation": 0.6123724356957945,
      "lift": 2.5,
      "conviction": 1.4545454545454546,
      "loe": 0.48718084308604387,
      "complexity": 1,
      "node": "[0;20)",
      "aux": {
        "rule": {
          "names": [
            "[0;20)",
            "[10;20)"
          ],
          "combinations": [
            30,
            10,
            0,
            10
          ]
        }
      },
      "operator": "none"
    },
    {
      "id": "id",
      "condition": "TRUE",
      "target": "[10;20)",
      "database_count": 50,
      "condition_count": 50,
      "target_count": 10,
      "intersection_count": 10,
      "support": 1,
      "confidence": 0.2,
      "correlation": 0,
      "lift": 1,
      "conviction": 0.975609756097561,
      "loe": 0.057131853609317905,
      "complexity": 1,
      "node": "TRUE",
      "aux": {
        "heatmap": {
          "tableData": [
            {
              "key": "[10;20)",
              "values": [
                {
                  "key": "[10;20)",
                  "value": 1
                },
                {
                  "key": "[0;20)",
                  "value": 0.6123724356957945
                }
              ]
            },
            {
              "key": "[0;20)",
              "values": [
                {
                  "key": "[10;20)",
                  "value": 0.6123724356957945
                },
                {
                  "key": "[0;20)",
                  "value": 1
                }
              ]
            }
          ],
          "rootData": {
            "totalLength": 1.5142135623730952,
            "children": [
              {
                "length": 1.4142135623730951,
                "key": "[10;20)"
              },
              {
                "length": 1.4142135623730951,
                "key": "[0;20)"
              }
            ]
          }
        },
        "upset": {
          "names": [
            "[10;20)",
            "[0;20)"
          ],
          "data": [
            {
              "id": [
                0
              ],
              "n": 10
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
              "n": 20
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
}""", Map::class.java
        )
        val expected = RulesLogger.GSON.fromJson(RulesLogger.GSON.toJson(expectedMap), Map::class.java)
        val actual = RulesLogger.GSON.fromJson(RulesLogger.GSON.toJson(actualMap), Map::class.java)
        assertEquals(expected, actual)
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
