package net.barrage.llmao.plugins

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.controllers.adminAgentsRoutes
import net.barrage.llmao.controllers.adminChatsRoutes
import net.barrage.llmao.controllers.adminUserRoutes
import net.barrage.llmao.controllers.agentsRoutes
import net.barrage.llmao.controllers.authRoutes
import net.barrage.llmao.controllers.chatsRoutes
import net.barrage.llmao.controllers.userRoutes
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.ChatService

// TODO: Create service state
fun Application.configureRouting(authService: AuthenticationService, chatService: ChatService) {
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

    authRoutes(authService)
    openApiRoutes()
    adminAgentsRoutes()
    agentsRoutes()
    adminUserRoutes()
    userRoutes()
    adminChatsRoutes(chatService)
    chatsRoutes(chatService)
  }
}
