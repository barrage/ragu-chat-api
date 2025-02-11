package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.util.toMap
import net.barrage.llmao.app.api.http.dto.ApplicationSettingsResponse
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

fun Route.adminSettingsRoutes(settings: Settings) {
  route("/admin/settings") {
    put(adminUpdateSettings()) {
      val parameters: SettingsUpdate = call.receive()
      settings.update(parameters)
      call.respond(HttpStatusCode.OK)
    }

    get(adminGetSettings()) {
      val parameters = call.request.queryParameters.toMap()["setting"]
      if (parameters.isNullOrEmpty()) {
        throw AppError.api(ErrorReason.InvalidParameter, "No setting keys provided")
      }
      val configuration = settings.get(parameters)
      val response = ApplicationSettingsResponse(configuration)
      call.respond(HttpStatusCode.OK, response)
    }
  }
}

private fun adminGetSettings(): OpenApiRoute.() -> Unit = {
  tags("admin/settings")
  description = "Retrieve application settings"
  request { queryParameter<String>("setting") { description = "Setting key" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Application settings retrieved successfully"
        body<ApplicationSettingsResponse>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving settings"
        body<List<AppError>> {}
      }
  }
}

private fun adminUpdateSettings(): OpenApiRoute.() -> Unit = {
  tags("admin/settings")
  description = "Update application settings"
  request { body<SettingsUpdate> { description = "Updated settings" } }
  response {
    HttpStatusCode.OK to { description = "Settings updated successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating settings"
        body<List<AppError>> {}
      }
  }
}
