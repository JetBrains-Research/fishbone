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
        RM.mine("foo", database, listOf(predicates to RangePredicate(20, 50)),
                { logger.log("id", it) }, maxComplexity = 3, topPerComplexity = 2)
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
      "error_type_1_count": 0,
      "error_type_2_count": 0,
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
        "marginals": {
          "[20;35)": 0.15,
          "[40;50)": 0.1,
          "[30;40)": 0.1,
          "[20;50)": 0.3
        },
        "probabilities": {
          "0000": 0.7000000000000004,
          "1000": 0.0,
          "0100": 0.0,
          "1100": 0.0,
          "0010": 0.0,
          "1010": 0.0,
          "0110": 0.0,
          "1110": 0.0,
          "0001": 0.0,
          "1001": 0.09999999999999999,
          "0101": 0.09999999999999999,
          "1101": 0.0,
          "0011": 0.05,
          "1011": 0.05,
          "0111": 0.0,
          "1111": 0.0
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
      "error_type_1_count": 0,
      "error_type_2_count": 5,
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
        "marginals": {
          "[20;35)": 0.15,
          "[40;50)": 0.1,
          "[20;50)": 0.3
        },
        "probabilities": {
          "000": 0.7000000000000004,
          "100": 0.0,
          "010": 0.0,
          "110": 0.0,
          "001": 0.05,
          "101": 0.15,
          "011": 0.09999999999999999,
          "111": 0.0
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
      "error_type_1_count": 0,
      "error_type_2_count": 15,
      "support": 0.15,
      "confidence": 1.0,
      "correlation": 0.641688947919748,
      "lift": 3.3333333333333335,
      "conviction": 10.5,
      "complexity": 1,
      "node": "[20;35)",
      "aux": {
        "[35;48)": {
          "marginals": {
            "[20;35)": 0.15,
            "[35;48)": 0.13,
            "[20;50)": 0.3
          },
          "probabilities": {
            "000": 0.7000000000000004,
            "100": 0.0,
            "010": 0.0,
            "110": 0.0,
            "001": 0.02,
            "101": 0.15,
            "011": 0.12999999999999998,
            "111": 0.0
          }
        }
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
      "error_type_1_count": 0,
      "error_type_2_count": 0,
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
        "marginals": {
          "[20;35)": 0.15,
          "[35;48)": 0.13,
          "[40;50)": 0.1,
          "[20;50)": 0.3
        },
        "probabilities": {
          "0000": 0.7000000000000004,
          "1000": 0.0,
          "0100": 0.0,
          "1100": 0.0,
          "0010": 0.0,
          "1010": 0.0,
          "0110": 0.0,
          "1110": 0.0,
          "0001": 0.0,
          "1001": 0.15,
          "0101": 0.05,
          "1101": 0.0,
          "0011": 0.02,
          "1011": 0.0,
          "0111": 0.08,
          "1111": 0.0
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
      "error_type_1_count": 0,
      "error_type_2_count": 2,
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
        "marginals": {
          "[20;35)": 0.15,
          "[35;48)": 0.13,
          "[20;50)": 0.3
        },
        "probabilities": {
          "000": 0.7000000000000004,
          "100": 0.0,
          "010": 0.0,
          "110": 0.0,
          "001": 0.02,
          "101": 0.15,
          "011": 0.12999999999999998,
          "111": 0.0
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
      "error_type_1_count": 0,
      "error_type_2_count": 17,
      "support": 0.13,
      "confidence": 1.0,
      "correlation": 0.5904735420248883,
      "lift": 3.3333333333333335,
      "conviction": 9.1,
      "complexity": 1,
      "node": "[35;48)",
      "aux": {}
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
