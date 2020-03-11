package org.jetbrains.bio.fishbone

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.internal.LinkedTreeMap
import joptsimple.OptionParser
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jetbrains.bio.util.*
import java.nio.file.Files

class TopResultsCleaner {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            OptionParser().apply {
                accepts("inputCsvFile", "File path to rules csv file")
                        .withRequiredArg().ofType(String::class.java)
                accepts("inputJsonFile", "File path to rules json file")
                        .withRequiredArg().ofType(String::class.java)
                accepts("filteredJsonFile", "File path to output filtered json file")
                        .withRequiredArg().ofType(String::class.java)
                accepts("criterion", "Choose loe or conviction")
                        .withRequiredArg().ofType(String::class.java)
            }.parse(args) { options ->
                val csvFilePath = options.valueOf("inputCsvFile").toString().toPath()
                val jsonFilePath = options.valueOf("inputJsonFile").toString().toPath()
                val filteredJsonFilePath = jsonFilePath.parent / ("filtered_" + jsonFilePath.name)
                val criterionColumnIndex = if (options.valueOf("criterion").toString() == "loe") 12 else 11

                val resultReader = Files.newBufferedReader(csvFilePath)
                resultReader.readLine()
                val results = CSVParser(resultReader!!, CSVFormat.DEFAULT.withDelimiter(','))
                        .map { csvRecord -> csvRecord }
                        .map { Pair(it[1], it[criterionColumnIndex].toDouble()) }
                        .sortedByDescending { it.second }
                results.forEach { println(it) }

                val topFeatures = results.map {
                    it.first.split(" AND ")
                            .map { feature -> feature.replace("_below_\\d+".toRegex(), "") to
                                    (feature to it.second) }
                }.flatten()
                        .groupBy({ it.first }, { it.second })
                        .mapValues { (_, values) -> values.maxBy { it.second } }
                        .values.map { it!!.first }

                val gson = Gson()
                val json = gson.fromJson(Files.newBufferedReader(jsonFilePath), HashMap<String, Any>().javaClass)
                val filteredRecords = (json["records"]!! as ArrayList<LinkedTreeMap<String, String>>).filter {
                    it["condition"]!!.split(" AND ").all { it in topFeatures }
                }
                var parents = filteredRecords.mapNotNull { it["parent_condition"] }
                parents = parents + (json["records"]!! as ArrayList<LinkedTreeMap<String, String>>)
                        .filter { it["condition"] in parents }.mapNotNull {
                            it["parent_condition"]
                        }
                json["records"] = filteredRecords + (json["records"]!! as ArrayList<LinkedTreeMap<String, String>>)
                        .filter { it["condition"] in parents }
                filteredJsonFilePath.write(GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
                                                   .create().toJson(json))
            }
        }
    }
}
