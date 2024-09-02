package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.controllers.agentsRoutes

fun Application.configureRouting() {
    install(Resources)
    routing {
        get("__health") {
            call.respond(HttpStatusCode.OK)
        }

        agentsRoutes()
    }
}