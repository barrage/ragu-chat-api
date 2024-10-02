package net.barrage.llmao.plugins

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.ServiceState
import net.barrage.llmao.app.api.http.controllers.adminAgentsRoutes
import net.barrage.llmao.app.api.http.controllers.adminChatsRoutes
import net.barrage.llmao.app.api.http.controllers.adminUserRoutes
import net.barrage.llmao.app.api.http.controllers.agentsRoutes
import net.barrage.llmao.app.api.http.controllers.authRoutes
import net.barrage.llmao.app.api.http.controllers.chatsRoutes
import net.barrage.llmao.app.api.http.controllers.userRoutes

// TODO: Create service state
fun Application.configureRouting(services: ServiceState) {
  install(Resources)
  routing {
    get(
      "__health",
      {
        tags("health")
        description = "Endpoint to check system health"
        response {
          HttpStatusCode.OK to { description = "Health check successful. No body content." }
        }
      },
    ) {
      call.respond(HttpStatusCode.OK)
    }

    authRoutes(services.auth)
    openApiRoutes()
    adminAgentsRoutes(services.agent)
    agentsRoutes(services.agent)
    adminUserRoutes(services.user)
    userRoutes(services.user)
    adminChatsRoutes(services.chat)
    chatsRoutes(services.chat)
  }
}
