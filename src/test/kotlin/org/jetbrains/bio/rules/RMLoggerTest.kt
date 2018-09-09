package org.jetbrains.bio.rules

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.awt.Color

/**
 * @author Oleg Shpynov
 * @date 16/12/2016
 */

class RMLoggerTest {

    @Test
    fun testGetJson() {

        val predicates = listOf(RangePredicate(20, 35), RangePredicate(35, 48)) +
                0.until(5).map { RangePredicate(it * 10, (it + 1) * 10) }
        val database = 0.until(100).toList()
        val logger = RMLogger(null)
        RM.mine("foo", database, listOf(predicates to RangePredicate(20, 50),
                predicates to RangePredicate(30, 80)),
                { logger.log("id", it) }, maxComplexity = 3, topPerComplexity = 5)
        assertEquals("""{
  "records": [
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
          "probabilities": [
            0.7000000000000004,
            0.0,
            0.0,
            0.0,
            0.0,
            0.05,
            0.20000000000000004,
            0.05
          ]
        }
      }
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
          "probabilities": [
            0.7000000000000004,
            0.0,
            0.0,
            0.0,
            0.05,
            0.09999999999999999,
            0.15,
            0.0
          ]
        }
      }
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
      "complexity": 1,
      "node": "[20;35)",
      "aux": {
        "rule": {
          "names": [
            "[20;35)",
            "[20;50)"
          ],
          "probabilities": [
            0.7000000000000004,
            0.0,
            0.15,
            0.15
          ]
        },
        "target": [
          {
            "names": [
              "[20;35)",
              "[35;48)",
              "[20;50)"
            ],
            "probabilities": [
              0.7000000000000004,
              0.0,
              0.0,
              0.0,
              0.02,
              0.15,
              0.12999999999999998,
              0.0
            ]
          }
        ]
      }
    },
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
      "complexity": 3,
      "node": "[40;50)",
      "parent_node": "[35;48)",
      "parent_condition": "[20;35) OR [35;48)",
      "aux": {
        "rule": {
          "names": [
            "[40;50)",
            "[20;35) OR [35;48)",
            "[20;50)"
          ],
          "probabilities": [
            0.7000000000000004,
            0.0,
            0.0,
            0.0,
            0.0,
            0.02,
            0.20000000000000004,
            0.08
          ]
        }
      }
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
          "probabilities": [
            0.7000000000000004,
            0.0,
            0.0,
            0.0,
            0.02,
            0.12999999999999998,
            0.15,
            0.0
          ]
        }
      }
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
      "complexity": 1,
      "node": "[35;48)",
      "aux": {
        "rule": {
          "names": [
            "[35;48)",
            "[20;50)"
          ],
          "probabilities": [
            0.7000000000000004,
            0.0,
            0.17,
            0.12999999999999998
          ]
        },
        "target": [
          {
            "names": [
              "[20;35)",
              "[35;48)",
              "[20;50)"
            ],
            "probabilities": [
              0.7000000000000004,
              0.0,
              0.0,
              0.0,
              0.02,
              0.15,
              0.12999999999999998,
              0.0
            ]
          }
        ]
      }
    }
  ],
  "palette": {
    "[35;48)": "#ffffff",
    "[30;40)": "#ffffff",
    "[20;50)": "#ffffff",
    "[40;50)": "#ffffff",
    "[20;35)": "#ffffff"
  }
}""", logger.getJson { Color.WHITE })
    }

}
