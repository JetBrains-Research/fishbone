package org.jetbrains.bio

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.*
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
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.experiments.rules.CiofaniExperiment
import org.jetbrains.bio.util.parse
import org.slf4j.event.Level
import java.io.File


class FishboneApp {
    private val defaultServerPort = 8080

    private val ciofaniExperiment = CiofaniExperiment()
    private val jacksonObjectMapper = jacksonObjectMapper()

    fun run(port: Int = defaultServerPort) {
        val server = embeddedServer(Netty, port) {
            install(CallLogging) {
                level = Level.INFO
            }
            install(ContentNegotiation) {
                jackson {}
            }
            install(CORS) {
                method(HttpMethod.Options)
                method(HttpMethod.Get)
                method(HttpMethod.Post)
                method(HttpMethod.Put)
                method(HttpMethod.Delete)
                method(HttpMethod.Patch)
                header(HttpHeaders.AccessControlAllowHeaders)
                header(HttpHeaders.ContentType)
                header(HttpHeaders.AccessControlAllowOrigin)
                allowCredentials = true
                anyHost()
            }
            routing {
                route("/rules") {
                    post("/mine") {
                        call.respond(mapOf("fishbone_filename" to processMineRuleRequest(call.receiveMultipart())))
                    }
                }
                route("/fishbone") {
                    get {
                        val filename: String = call.request.queryParameters["filename"]!!
                        val file = File("$filename")
                        if (file.exists()) {
                            call.respondFile(file)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        }
        server.start(wait = true)
    }

    private suspend fun processMineRuleRequest(multipart: MultiPartData): String {
        val tempDir = createTempDir("temp-${System.currentTimeMillis()}")
        val request = combineMineRulesRequest(multipart, tempDir)
        val result = when (request.experiment) {
            "ciofani" -> ciofaniExperiment.run(request)
            else -> throw IllegalArgumentException("Unexpected expirement name")
        }
        tempDir.deleteRecursively()
        return result
    }

    private suspend fun combineMineRulesRequest(multipart: MultiPartData, tempDir: File): MineRulesRequest {
        val requestMap = mutableMapOf<String, Any>()
        multipart.forEachPart { part ->
            val name = part.name!!
            when (part) {
                is PartData.FormItem -> {
                    requestMap[name] = part.value
                }
                is PartData.FileItem -> {
                    val file = File(tempDir, part.originalFileName)
                    part.streamProvider().use { its ->
                        file.outputStream().buffered().use { its.copyTo(it) }
                    }
                    requestMap[name] = if (name == "database") {
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
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            OptionParser().apply {
                accepts("port", "Server port (default 8080)").withOptionalArg()
                formatHelpWith(BuiltinHelpFormatter(200, 2))
            }.parse(args) { options ->
                FishboneApp().run(options.valueOf("port").toString().toInt())
            }
        }
    }


}
