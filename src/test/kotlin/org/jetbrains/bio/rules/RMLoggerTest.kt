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

        val predicates = listOf(RangePredicate(0, 10), RangePredicate(10, 20))
        val database = 0.until(100).toList()
        val logger = RMLogger(null)
        RM.mine("foo", database, listOf(predicates to RangePredicate(0, 20)),
                { logger.log("id", it) }, maxComplexity = 10, topResults = 1)
        assertEquals("""{
  "records": [
    {
      "id": "id",
      "condition": "[0;10) OR [10;20)",
      "target": "[0;20)",
      "database_count": 100,
      "condition_count": 20,
      "target_count": 20,
      "intersection_count": 20,
      "error_type_1_count": 0,
      "error_type_2_count": 0,
      "support": 0.2,
      "confidence": 1.0,
      "correlation": 1.0,
      "lift": 5.0,
      "conviction": 16.0,
      "complexity": 2,
      "node": "[10;20)",
      "parent_node": "[0;10)",
      "parent_condition": "[0;10)"
    },
    {
      "id": "id",
      "condition": "[0;10)",
      "target": "[0;20)",
      "database_count": 100,
      "condition_count": 10,
      "target_count": 20,
      "intersection_count": 10,
      "error_type_1_count": 0,
      "error_type_2_count": 10,
      "support": 0.1,
      "confidence": 1.0,
      "correlation": 0.6666666666666666,
      "lift": 5.0,
      "conviction": 8.0,
      "complexity": 1,
      "node": "[0;10)"
    }
  ],
  "palette": {
    "[0;10)": "#ffffff",
    "[0;20)": "#ffffff",
    "[10;20)": "#ffffff"
  }
}""", logger.getJson { Color.WHITE })
    }

}
