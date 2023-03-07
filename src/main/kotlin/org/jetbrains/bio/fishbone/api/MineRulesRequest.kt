package org.jetbrains.bio.fishbone.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.content.*
import java.io.File

/**
 * Represents request to mine associations
 */
class MineRulesRequest(
    val experiment: ExperimentType,
    val genome: String,
    val predicates: List<String>,
    val targets: List<String>,
    val database: String,
    val miners: Set<MiningAlgorithm>,
    val criterion: String,
    val significanceLevel: Double?,
    val runName: String,
    val settings: ExperimentSettings = ExperimentSettings()
) {
    companion object {
        private val jacksonObjectMapper = jacksonObjectMapper()

        /**
         * Method to construct MineRulesRequest from MultiPartData request
         *
         * @param multipart data based on which request is constructed
         * @param tempDir temporary directory to store predicate files during mining
         *
         * @return constructed MineRulesRequest
         */
        suspend fun fromMultiPartData(multipart: MultiPartData, tempDir: File): MineRulesRequest {
            val requestMap = mutableMapOf<String, Any>()
            val settingsMap = mutableMapOf<String, Any>()
            multipart.forEachPart { part ->
                val name = part.name!!
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "miners") {
                            requestMap[name] = part.value.split(", ").map { MiningAlgorithm.byLable(it) }.toSet()
                        } else {
                            if (part.name!! in ExperimentSettings::class.java.declaredFields.map { it.name }) {
                                settingsMap[name] = part.value
                            } else {
                                requestMap[name] = part.value
                            }
                        }
                    }

                    is PartData.FileItem -> {
                        val file = createFile(tempDir, part)
                        requestMap[name] = if (name == "database") {
                            file.absolutePath
                        } else {
                            (requestMap.getOrDefault(name, listOf<String>()) as List<String>) + file.absolutePath
                        }
                    }
                }
            }
            if (settingsMap.isNotEmpty()) {
                requestMap["settings"] = settingsMap
            }

            return jacksonObjectMapper.convertValue(requestMap, MineRulesRequest::class.java)
        }

        private fun createFile(tempDir: File, part: PartData.FileItem): File {
            val file = File(tempDir, part.originalFileName)
            part.streamProvider().use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
            return file
        }
    }
}