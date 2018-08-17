package org.jetbrains.bio.rules

import com.google.common.collect.ObjectArrays
import com.google.gson.GsonBuilder
import org.apache.commons.csv.CSVFormat.DEFAULT
import org.apache.log4j.Logger
import org.jetbrains.bio.util.bufferedWriter
import org.jetbrains.bio.util.deleteIfExists
import org.jetbrains.bio.util.write
import java.awt.Color
import java.io.IOException
import java.nio.file.Path

/**
 * Class to log predicates or trees to csv and json suitable for rules_browser rendering
 */
class RMLogger(val path: Path?, vararg params: String) {
    private val extraParams: Array<out String> = params

    companion object {
        private val LOG = Logger.getLogger(RMLogger::class.java)
    }

    private val graphRecords = LinkedHashSet<Map<String, Any?>>()
    private val atomics = hashSetOf<String>()

    private val csvPrinter by lazy {
        DEFAULT.withCommentMarker('#')
                .withHeader(*ObjectArrays.concat(RuleRecord.PARAMS.toTypedArray(), extraParams, String::class.java))
                .print(path!!.bufferedWriter())
    }

    @Synchronized
    fun log(id: String, tree: List<RM.Node<*>>) {
        val visited = hashSetOf<String>()
        tree.forEach { n ->
            var node: RM.Node<*>? = n
            while (node != null) {
                val rule = node.rule
                val r = rule.toRecord(id = id)
                if (r.conditionPredicate.name() in visited) {
                    break
                }
                visited.add(r.conditionPredicate.name())
                atomics.addAll(r.conditionPredicate.collectAtomics().map { it.name() })
                atomics.addAll(r.targetPredicate.collectAtomics().map { it.name() })
                // Log to csv
                log(r)
                // Json based visualization
                graphRecords.add(r.toMap() + mapOf(
                        "node" to node.element.name(),
                        "parent_node" to if (node.parent != null) node.parent!!.element.name() else null,
                        "parent_condition" to if (node.parent != null) node.parent!!.rule.conditionPredicate.name() else null))
                node = node.parent
            }
        }
    }

    @Synchronized
    fun log(id: String, rule: Rule<*>, vararg info: String) {
        log(rule.toRecord(id), *info)
    }

    @Synchronized
    fun log(record: RuleRecord<*>, vararg info: String) {
        if (path != null) {
            try {
                val printer = csvPrinter
                printer.printRecord(*(record.toCSV() + info).toTypedArray())
                printer.flush()
            } catch (e: IOException) {
                LOG.error(e.message, e)
            }
        }
    }

    fun done(jsonPath: Path?, palette: (String) -> Color) {
        if (path != null) {
            csvPrinter.close()
        }
        if (jsonPath != null) {
            jsonPath.deleteIfExists()
            jsonPath.write(getJson(palette))
        }
    }

    private fun Color.toHex(): String = "#%02x%02x%02x".format(red, green, blue)

    fun getJson(palette: (String) -> Color): String {
        // We want null modification to map to "null"
        val json = mapOf(
                "records" to graphRecords.toList(),
                "palette" to atomics.associateBy({ it }, { palette(it).toHex() })
        )
        return GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(json)
    }

}
