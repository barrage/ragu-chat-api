package net.barrage.llmao.plugins

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.ServiceState
import net.barrage.llmao.app.api.http.controllers.adminAgentsRoutes
import net.barrage.llmao.app.api.http.controllers.adminChatsRoutes
import net.barrage.llmao.app.api.http.controllers.adminUserRoutes
import net.barrage.llmao.app.api.http.controllers.administrationRouter
import net.barrage.llmao.app.api.http.controllers.agentsRoutes
import net.barrage.llmao.app.api.http.controllers.authRoutes
import net.barrage.llmao.app.api.http.controllers.chatsRoutes
import net.barrage.llmao.app.api.http.controllers.devController
import net.barrage.llmao.app.api.http.controllers.userRoutes
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

fun Application.configureRouting(services: ServiceState) {
  install(Resources)

  routing {
    // K8S specific route
    get("__health") { call.respond(HttpStatusCode.OK) }

    authRoutes(services.auth)
    openApiRoutes()

    authenticate("auth-session-admin") {
      adminAgentsRoutes(services.agent)
      adminUserRoutes(services.user)
      adminChatsRoutes(services.chat)
      administrationRouter(services.admin)
    }

    authenticate("auth-session") {
      agentsRoutes(services.agent)
      userRoutes(services.user)
      chatsRoutes(services.chat)
    }

    if (application.environment.config.property("ktor.environment").getString() == "development") {
      devController(services.auth, services.user)
    }
  }
}

/**
 * Utility for quickly obtaining a path segment from a URL and converting it to a UUID. Throws an
 * [Error] if the UUID is malformed.
 */
fun ApplicationCall.pathUuid(param: String): KUUID {
  val value = parameters[param]
  try {
    return KUUID.fromString(value)
  } catch (e: IllegalArgumentException) {
    throw AppError.api(ErrorReason.InvalidParameter, "'$value' is not a valid UUID")
  }
}
