package org.jetbrains.bio.rules

import junit.framework.TestCase.assertEquals
import org.jetbrains.bio.predicates.FalsePredicate
import org.junit.Test
import java.awt.Color

/**
 * @author Oleg Shpynov
 * @date 16/12/2016
 */

class RulesLoggerTest {

    @Test
    fun testGetJson() {

        val predicates = listOf(RangePredicate(20, 35), RangePredicate(35, 48)) +
                0.until(5).map { RangePredicate(it * 10, (it + 1) * 10) }
        val database = 0.until(100).toList()
        val logger = RulesLogger(null)
        FishboneMiner.mine("foo", database, listOf(predicates to RangePredicate(20, 50)),
                { logger.log("id", it) }, maxComplexity = 3, topPerComplexity = 2)
        assertEquals("""{
  "records": [
    {
      "id": "id",
      "condition": "[20;35) OR [35;48) OR [40;50)",
      "target": "[20;50)",
      "database_count": 100,
      "condition_count": 30,
      "target_count": 30,
      "intersection_count": 30,
      "support": 0.3,
      "confidence": 1.0,
      "correlation": 1.0,
      "lift": 3.3333333333333335,
      "conviction": 21.0,
      "loe": 1.4926612809863424,
      "complexity": 3,
      "node": "[35;48)",
      "parent_node": "[40;50)",
      "parent_condition": "[20;35) OR [40;50)",
      "aux": {
        "rule": {
          "names": [
            "[35;48)",
            "[20;35) OR [40;50)",
            "[20;50)"
          ],
          "combinations": [
            70,
            0,
            0,
            0,
            0,
            5,
            17,
            8
          ]
        }
      },
      "operator": "or"
    },
    {
      "id": "id",
      "condition": "[20;35) OR [40;50)",
      "target": "[20;50)",
      "database_count": 100,
      "condition_count": 25,
      "target_count": 30,
      "intersection_count": 25,
      "support": 0.25,
      "confidence": 1.0,
      "correlation": 0.8819171036881969,
      "lift": 3.3333333333333335,
      "conviction": 17.5,
      "loe": 1.44600441693014,
      "complexity": 2,
      "node": "[40;50)",
      "parent_node": "[20;35)",
      "parent_condition": "[20;35)",
      "aux": {
        "rule": {
          "names": [
            "[40;50)",
            "[20;35)",
            "[20;50)"
          ],
          "combinations": [
            70,
            0,
            0,
            0,
            5,
            10,
            15,
            0
          ]
        }
      },
      "operator": "or"
    },
    {
      "id": "id",
      "condition": "[20;35)",
      "target": "[20;50)",
      "database_count": 100,
      "condition_count": 15,
      "target_count": 30,
      "intersection_count": 15,
      "support": 0.15,
      "confidence": 1.0,
      "correlation": 0.641688947919748,
      "lift": 3.3333333333333335,
      "conviction": 10.5,
      "loe": 1.3085643790137544,
      "complexity": 1,
      "node": "[20;35)",
      "aux": {
        "rule": {
          "names": [
            "[20;35)",
            "[20;50)"
          ],
          "combinations": [
            70,
            0,
            15,
            15
          ]
        },
        "target": {
          "names": [
            "[35;48)",
            "[20;35)"
          ],
          "data": [
            {
              "first": [
                0
              ],
              "second": 30
            },
            {
              "first": [
                0,
                2
              ],
              "second": 15
            },
            {
              "first": [
                0,
                1
              ],
              "second": 13
            },
            {
              "first": [
                2
              ],
              "second": 15
            },
            {
              "first": [
                1
              ],
              "second": 13
            }
          ]
        }
      },
      "operator": "none"
    },
    {
      "id": "id",
      "condition": "[20;35) OR [30;40) OR [40;50)",
      "target": "[20;50)",
      "database_count": 100,
      "condition_count": 30,
      "target_count": 30,
      "intersection_count": 30,
      "support": 0.3,
      "confidence": 1.0,
      "correlation": 1.0,
      "lift": 3.3333333333333335,
      "conviction": 21.0,
      "loe": 1.4926612809863424,
      "complexity": 3,
      "node": "[30;40)",
      "parent_node": "[40;50)",
      "parent_condition": "[20;35) OR [40;50)",
      "aux": {
        "rule": {
          "names": [
            "[30;40)",
            "[20;35) OR [40;50)",
            "[20;50)"
          ],
          "combinations": [
            70,
            0,
            0,
            0,
            0,
            5,
            20,
            5
          ]
        }
      },
      "operator": "or"
    },
    {
      "id": "id",
      "condition": "[20;35) OR [35;48)",
      "target": "[20;50)",
      "database_count": 100,
      "condition_count": 28,
      "target_count": 30,
      "intersection_count": 28,
      "support": 0.28,
      "confidence": 1.0,
      "correlation": 0.9525793444156804,
      "lift": 3.3333333333333335,
      "conviction": 19.6,
      "loe": 1.4751207883642163,
      "complexity": 2,
      "node": "[35;48)",
      "parent_node": "[20;35)",
      "parent_condition": "[20;35)",
      "aux": {
        "rule": {
          "names": [
            "[35;48)",
            "[20;35)",
            "[20;50)"
          ],
          "combinations": [
            70,
            0,
            0,
            0,
            2,
            13,
            15,
            0
          ]
        }
      },
      "operator": "or"
    },
    {
      "id": "id",
      "condition": "[35;48)",
      "target": "[20;50)",
      "database_count": 100,
      "condition_count": 13,
      "target_count": 30,
      "intersection_count": 13,
      "support": 0.13,
      "confidence": 1.0,
      "correlation": 0.5904735420248883,
      "lift": 3.3333333333333335,
      "conviction": 9.1,
      "loe": 1.2677161841198006,
      "complexity": 1,
      "node": "[35;48)",
      "aux": {
        "rule": {
          "names": [
            "[35;48)",
            "[20;50)"
          ],
          "combinations": [
            70,
            0,
            17,
            13
          ]
        },
        "target": {
          "names": [
            "[35;48)",
            "[20;35)"
          ],
          "data": [
            {
              "first": [
                0
              ],
              "second": 30
            },
            {
              "first": [
                0,
                2
              ],
              "second": 15
            },
            {
              "first": [
                0,
                1
              ],
              "second": 13
            },
            {
              "first": [
                2
              ],
              "second": 15
            },
            {
              "first": [
                1
              ],
              "second": 13
            }
          ]
        }
      },
      "operator": "none"
    }
  ],
  "palette": {
    "[35;48)": "#ffffff",
    "[30;40)": "#ffffff",
    "[20;50)": "#ffffff",
    "[40;50)": "#ffffff",
    "[20;35)": "#ffffff"
  },
  "criterion": "conviction"
}""", logger.getJson({ Color.WHITE }))
    }


    @Test
    fun testRuleRecordCSV() {
        val rule = Rule(FalsePredicate<Any>().named("foo"), FalsePredicate<Any>().named("bar"), 10, 8, 9, 7)
        assertEquals(listOf("id", "foo", "bar", 10, 8, 9, 7, 0.8, 0.875,
                -0.16666666666666666, 0.9722222222222222, 0.4, 0.22427683792970576, 1),
                RuleRecord.fromRule(rule, "id").toCSV())
    }

    @Test
    fun testRuleRecordMap() {
        val rule = Rule(FalsePredicate<Any>().named("foo"), FalsePredicate<Any>().named("bar"), 10, 8, 9, 7)
        assertEquals("{id=id, condition=foo, target=bar, " +
                "database_count=10, condition_count=8, target_count=9, intersection_count=7, " +
                "support=0.8, confidence=0.875, " +
                "correlation=-0.16666666666666666, lift=0.9722222222222222, conviction=0.4, loe=0.22427683792970576, " +
                "complexity=1}",
                RuleRecord.fromRule(rule, "id").toMap().toString())
    }

}
