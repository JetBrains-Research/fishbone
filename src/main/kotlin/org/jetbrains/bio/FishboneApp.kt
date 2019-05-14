package org.jetbrains.bio

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.jackson
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.jetbrains.bio.api.ExperimentType
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.Miner
import org.jetbrains.bio.experiments.rules.ChiantiDataExperiment
import org.jetbrains.bio.experiments.rules.CiofaniDataExperiment
import org.jetbrains.bio.experiments.rules.Experiment
import org.jetbrains.bio.util.parse
import java.io.File


class FishboneApp(private val experiments: Map<ExperimentType, Experiment>) {
    private val jacksonObjectMapper = jacksonObjectMapper()

    fun run(port: Int = defaultServerPort) {
        val server = embeddedServer(Netty, port) {
            install(CallLogging)
            install(ContentNegotiation) {
                jackson {}
            }
            install(CORS) {
                method(HttpMethod.Options)
                method(HttpMethod.Get)
                method(HttpMethod.Post)
                header(HttpHeaders.AccessControlAllowHeaders)
                header(HttpHeaders.ContentType)
                header(HttpHeaders.AccessControlAllowOrigin)
                allowCredentials = true
                anyHost()
            }
            routing {
                route("/rules") {
                    get {
                        val filename = call.request.queryParameters["filename"]!!
                        call.respondFile(File(filename))
                    }
                    post {
                        call.respond(mineRules(call.receiveMultipart()))
                    }
                }
            }
        }
        server.start(wait = true)
    }

    // TODO: run miners async
    private suspend fun mineRules(multipart: MultiPartData): Map<Miner, String> {
        val tempDir = createTempDir("temp-${System.currentTimeMillis()}")
        val request = multipartToMineRulesRequest(multipart, tempDir)
        val experiment = experiments[request.experiment] ?: throw IllegalArgumentException("Unexpected experiment name")
        val result = experiment.run(request)
        tempDir.deleteRecursively()
        return result
    }

    private suspend fun multipartToMineRulesRequest(multipart: MultiPartData, tempDir: File): MineRulesRequest {
        val requestMap = mutableMapOf<String, Any>()
        multipart.forEachPart { part ->
            val name = part.name!!
            when (part) {
                is PartData.FormItem -> {
                    if (part.name == "miners") {
                        requestMap[name] =
                            part.value.split(", ").map { Miner.byLable(it) }.toSet() //TODO: process array correctly
                    } else {
                        requestMap[name] = part.value
                    }
                }
                is PartData.FileItem -> {
                    val file = File(tempDir, part.originalFileName)
                    part.streamProvider().use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
                    requestMap[name] = if (name == "database" || name == "target") {
                        file.absolutePath
                    } else {
                        (requestMap.getOrDefault(name, listOf<String>()) as List<String>) + file.absolutePath
                    }
                }
            }
        }
        return jacksonObjectMapper.convertValue<MineRulesRequest>(requestMap, MineRulesRequest::class.java)
    }

    companion object {
        private const val defaultServerPort = 8080
        private const val defaultOutputFolder = "."

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {

            OptionParser().apply {
                accepts("port", "Server port (default 8080)").withOptionalArg().ofType(Int::class.java)
                    .defaultsTo(defaultServerPort)
                accepts(
                    "output",
                    "Output folder to store experiment's results (default - current directory)"
                ).withOptionalArg().ofType(String::class.java)
                    .defaultsTo(defaultOutputFolder)
                formatHelpWith(BuiltinHelpFormatter(200, 2))
            }.parse(args) { options ->
                val outputFolder = options.valueOf("output").toString()
                val experiments = mapOf(
                    ExperimentType.CIOFANI to CiofaniDataExperiment(outputFolder),
                    ExperimentType.CHIANTI to ChiantiDataExperiment(outputFolder)
                )
                val port = options.valueOf("port").toString().toInt()
                FishboneApp(experiments).run(port)
            }
        }
    }


}
