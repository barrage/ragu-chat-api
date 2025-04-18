package net.barrage.llmao.app.api.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.Adapters
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.adminWhatsAppRoutes
import net.barrage.llmao.app.adapters.whatsapp.whatsAppHookRoutes
import net.barrage.llmao.app.adapters.whatsapp.whatsAppRoutes
import net.barrage.llmao.app.api.http.controllers.adminAgentsRoutes
import net.barrage.llmao.app.api.http.controllers.adminChatsRoutes
import net.barrage.llmao.app.api.http.controllers.adminSettingsRoutes
import net.barrage.llmao.app.api.http.controllers.administrationRouter
import net.barrage.llmao.app.api.http.controllers.agentsRoutes
import net.barrage.llmao.app.api.http.controllers.avatarRoutes
import net.barrage.llmao.app.api.http.controllers.chatsRoutes
import net.barrage.llmao.app.api.http.controllers.specialistWorkflowRoutes
import net.barrage.llmao.app.api.http.controllers.specialists.jiraKiraAdminRoutes
import net.barrage.llmao.app.api.http.controllers.specialists.jiraKiraUserRoutes
import net.barrage.llmao.app.workflow.jirakira.JiraKiraWorkflowFactory
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
    authenticate("admin") {
      adminAgentsRoutes(state.services.admin.agent, state.services.admin.settings)
      adminChatsRoutes(state.services.admin.chat)
      administrationRouter(state.services.admin.admin)
      adminSettingsRoutes(state.services.admin.settings)
      Adapters.runIfEnabled<JiraKiraWorkflowFactory> { jiraKiraAdminRoutes(it.jiraKiraRepository) }
    }

    // User API routes
    authenticate("user") {
      specialistWorkflowRoutes()
      Adapters.runIfEnabled<JiraKiraWorkflowFactory> { jiraKiraUserRoutes(it.jiraKiraRepository) }

      agentsRoutes(state.services.user.agent)
      chatsRoutes(state.services.user.chat, state.providers.image)
    }

    avatarRoutes(state.providers.image)

    // WhatsApp API routes
    Adapters.runIfEnabled<WhatsAppAdapter> { whatsAppAdapter ->
      whatsAppHookRoutes(whatsAppAdapter)
      authenticate("admin") { adminWhatsAppRoutes(whatsAppAdapter) }
      authenticate("user") { whatsAppRoutes(whatsAppAdapter) }
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
    throw AppError.api(ErrorReason.InvalidParameter, "'$value' is not a valid UUID", original = e)
  }
}
