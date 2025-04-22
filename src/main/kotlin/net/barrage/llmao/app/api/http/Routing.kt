package net.barrage.llmao.app.api.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.Plugins
import net.barrage.llmao.app.workflow.chat.controllers.adminSettingsRoutes
import net.barrage.llmao.app.workflow.chat.controllers.avatarRoutes
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.types.KUUID

fun Application.configureRouting(state: ApplicationState) {
  routing {
    route("/__health") { get { call.respond(HttpStatusCode.OK) } }

    // Add swagger-ui only if we're not in production.
    if (application.environment.config.property("ktor.environment").getString() != "production") {
      openApiRoutes()
    }

    // Admin API routes
    authenticate("admin") { adminSettingsRoutes(state.settings) }

    avatarRoutes(state.providers.image)

    with(Plugins) { route(state) }
  }
}

/**
 * Utility for quickly obtaining a path segment from a URL and converting it to a UUID. Throws an
 * [AppError] if the UUID is malformed.
 */
fun ApplicationCall.pathUuid(param: String): KUUID {
  val value = parameters[param]
  try {
    return KUUID.fromString(value)
  } catch (e: IllegalArgumentException) {
    throw AppError.api(ErrorReason.InvalidParameter, "'$value' is not a valid UUID", original = e)
  }
}
