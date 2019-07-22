package org.jetbrains.bio

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.MultiPartData
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
import io.ktor.util.pipeline.PipelineContext
import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.jetbrains.bio.api.ExperimentType
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.api.MiningAlgorithm
import org.jetbrains.bio.experiment.ChiantiDataExperiment
import org.jetbrains.bio.experiment.GenomeBasedExperiment
import org.jetbrains.bio.experiment.Experiment
import org.jetbrains.bio.util.parse
import java.io.File

/**
 * Main class for Fishbone application.
 * Contains starter function and HTTP requests handling
 */
class FishboneApp(private val experiments: Map<ExperimentType, Experiment>, private val outputFolder: String) {

    /**
     * HTTP API for Fishbone service
     */
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
                    // API to load rules from specified file
                    get {
                        val file = loadRules()
                        call.respondFile(file)
                    }
                    // API to mine rules on spesified data
                    post {
                        call.respond(mineRules(call.receiveMultipart()))
                    }
                }
            }
        }
        server.start(wait = true)
    }

    /**
     * Loads file with rules.
     */
    private fun PipelineContext<Unit, ApplicationCall>.loadRules(): File {
        val fileName = call.request.queryParameters["filename"]
                ?: throw IllegalArgumentException("Filename parameter is expected")
        return if (fileName.contains(outputFolder)) {
            File(fileName)
        } else {
            val experimentName = call.request.queryParameters["experiment"]
                    ?: throw IllegalArgumentException("Experiment parameter is expected")
            val experimentType = ExperimentType.valueOf(experimentName)
            val experiment = experiments[experimentType]
                    ?: throw IllegalArgumentException("Unexpected experiment name")
            File("${experiment.outputFolder}/$fileName")
        }
    }

    /**
     * Mine rules based on parameters from the request
     */
    private suspend fun mineRules(multipart: MultiPartData): Map<MiningAlgorithm, String> {
        val tempDir = createTempDir("temp-${System.currentTimeMillis()}")
        val request = MineRulesRequest.fromMultiPartData(multipart, tempDir)
        val experiment = experiments[request.experiment] ?: throw IllegalArgumentException("Unexpected experiment name")
        val result = experiment.run(request)
        tempDir.deleteRecursively()
        return result
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
                        ExperimentType.GENOME to GenomeBasedExperiment(outputFolder),
                        ExperimentType.CHIANTI to ChiantiDataExperiment(outputFolder)
                )
                val port = options.valueOf("port").toString().toInt()
                FishboneApp(experiments, outputFolder).run(port)
            }
        }
    }


}
