package net.barrage.llmao.app.api.http

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.adapters.chonkit.ChonkitAuthenticationService
import net.barrage.llmao.app.adapters.chonkit.chonkitAuthRouter
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.api.adminWhatsAppRoutes
import net.barrage.llmao.app.adapters.whatsapp.api.whatsAppHookRoutes
import net.barrage.llmao.app.adapters.whatsapp.api.whatsAppRoutes
import net.barrage.llmao.app.api.http.controllers.adminAgentsRoutes
import net.barrage.llmao.app.api.http.controllers.adminChatsRoutes
import net.barrage.llmao.app.api.http.controllers.adminSettingsRoutes
import net.barrage.llmao.app.api.http.controllers.adminUserRoutes
import net.barrage.llmao.app.api.http.controllers.administrationRouter
import net.barrage.llmao.app.api.http.controllers.agentsRoutes
import net.barrage.llmao.app.api.http.controllers.authRoutes
import net.barrage.llmao.app.api.http.controllers.chatsRoutes
import net.barrage.llmao.app.api.http.controllers.devController
import net.barrage.llmao.app.api.http.controllers.imageRoutes
import net.barrage.llmao.app.api.http.controllers.specialistWorkflowRoutes
import net.barrage.llmao.app.api.http.controllers.specialists.jiraKiraAdminRoutes
import net.barrage.llmao.app.api.http.controllers.specialists.jiraKiraUserRoutes
import net.barrage.llmao.app.api.http.controllers.thirdPartyRoutes
import net.barrage.llmao.app.api.http.controllers.userRoutes
import net.barrage.llmao.app.workflow.jirakira.JiraKiraWorkflowFactory
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

fun Application.configureRouting(state: ApplicationState) {
  routing {
    // K8S specific route
    route("/__health") { get { call.respond(HttpStatusCode.OK) } }

    // Unprotected authentication routes
    authRoutes(state.services.auth, state.adapters)

    thirdPartyRoutes()

    // Add swagger-ui only if we're not in production.
    if (application.environment.config.property("ktor.environment").getString() != "production") {
      openApiRoutes()
    }

    // Admin API routes
    authenticate("auth-session-admin") {
      adminAgentsRoutes(state.services.agent, state.settingsService)
      adminUserRoutes(state.services.user)
      adminChatsRoutes(state.services.chat)
      administrationRouter(state.services.admin)
      adminSettingsRoutes(state.settingsService)
      state.adapters.runIfEnabled<ChonkitAuthenticationService, Unit> { chonkitAuthRouter(it) }
      state.adapters.runIfEnabled<JiraKiraWorkflowFactory, Unit> {
        jiraKiraAdminRoutes(it.jiraKiraRepository)
      }
    }

    // User API routes
    authenticate("auth-session") {
      specialistWorkflowRoutes(state.adapters)
      state.adapters.runIfEnabled<JiraKiraWorkflowFactory, Unit> {
        jiraKiraUserRoutes(it.jiraKiraRepository)
      }

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
