package net.barrage.llmao.plugins

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService
import net.barrage.llmao.adapters.chonkit.chonkitAuthRouter
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.api.adminWhatsAppRoutes
import net.barrage.llmao.app.adapters.whatsapp.api.whatsAppHookRoutes
import net.barrage.llmao.app.adapters.whatsapp.api.whatsAppRoutes
import net.barrage.llmao.app.api.http.controllers.adminAgentsRoutes
import net.barrage.llmao.app.api.http.controllers.adminChatsRoutes
import net.barrage.llmao.app.api.http.controllers.adminUserRoutes
import net.barrage.llmao.app.api.http.controllers.administrationRouter
import net.barrage.llmao.app.api.http.controllers.agentsRoutes
import net.barrage.llmao.app.api.http.controllers.authRoutes
import net.barrage.llmao.app.api.http.controllers.chatsRoutes
import net.barrage.llmao.app.api.http.controllers.devController
import net.barrage.llmao.app.api.http.controllers.imageRoutes
import net.barrage.llmao.app.api.http.controllers.thirdPartyRoutes
import net.barrage.llmao.app.api.http.controllers.userRoutes
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

fun Application.configureRouting(state: ApplicationState) {
  routing {
    // K8S specific route
    route("/__health") { get(health()) { call.respond(HttpStatusCode.OK) } }

    // Unprotected authentication routes
    authRoutes(state.services.auth, state.adapters)

    thirdPartyRoutes()

    // Add swagger-ui only if we're not in production.
    if (application.environment.config.property("ktor.environment").getString() != "production") {
      openApiRoutes()
    }

    // Admin API routes
    authenticate("auth-session-admin") {
      adminAgentsRoutes(state.services.agent)
      adminUserRoutes(state.services.user)
      adminChatsRoutes(state.services.chat)
      administrationRouter(state.services.admin)
      state.adapters.runIfEnabled<ChonkitAuthenticationService, Unit> { chonkitAuthRouter(it) }
    }

    // User API routes
    authenticate("auth-session") {
      agentsRoutes(state.services.agent)
      userRoutes(state.services.user)
      chatsRoutes(state.services.chat)
    }

    imageRoutes(state.providers.imageStorage)
    // WhatsApp API routes
    state.adapters.runIfEnabled<WhatsAppAdapter, Unit> { whatsAppAdapter ->
      whatsAppHookRoutes(whatsAppAdapter)
      authenticate("auth-session-admin") { adminWhatsAppRoutes(whatsAppAdapter) }
      authenticate("auth-session") { whatsAppRoutes(whatsAppAdapter) }
    }

    if (application.environment.config.property("ktor.environment").getString() == "development") {
      devController(state.services.auth, state.services.user, state.adapters)
    }
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
    throw AppError.api(ErrorReason.InvalidParameter, "'$value' is not a valid UUID")
  }
}

fun ApplicationCall.expireCookie(name: String) {
  response.cookies.append(
    Cookie(
      name = name,
      value = "",
      encoding = CookieEncoding.RAW,
      maxAge = 0,
      expires = GMTDate(),
      domain = null,
      path = null,
      secure = false,
      httpOnly = true,
      extensions = mapOf(),
    )
  )
}

private fun health(): OpenApiRoute.() -> Unit = {
  description = "Health check for server"
  summary = "Health check"
  securitySchemeNames = null
  response { HttpStatusCode.OK to { description = "Server is healthy" } }
}
