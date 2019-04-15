package org.jetbrains.bio

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.jetbrains.bio.api.MineRulesRequest
import org.jetbrains.bio.util.parse


class FishboneApp {
    private val defaultServerPort = 8080

    fun run(port: Int = defaultServerPort) {
        val server = embeddedServer(Netty, port) {
            install(CallLogging)
            install(ContentNegotiation) {
                jackson {}
            }
            routing {
                route("/rules") {
                    get {
                        call.respond(mapOf("Rules" to "Hi!"))
                    }
                    post("/mine") {
                        val mineRulesRequest = call.receive<MineRulesRequest>()
                        call.respond(mapOf("Source" to mineRulesRequest.source, "Target" to mineRulesRequest.target))
                    }
                }
            }
        }
        server.start(wait = true)
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
