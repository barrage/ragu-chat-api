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
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.settings.SettingsUpdate

fun Route.adminSettingsRoutes(settingsService: Settings) {
  route("/admin/settings") {
    put(updateSettings()) {
      val parameters: SettingsUpdate = call.receive()
      settingsService.update(parameters)
      call.respond(HttpStatusCode.OK)
    }

    get(getSettings()) {
      val settings = settingsService.getAllWithDefaults()
      call.respond(HttpStatusCode.OK, settings)
    }
  }
}

private fun updateSettings(): OpenApiRoute.() -> Unit = {
  tags("admin/settings")
  description = "Update settings"
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

private fun getSettings(): OpenApiRoute.() -> Unit = {
  tags("admin/settings")
  description = "Retrieve settings"
  response {
    HttpStatusCode.OK to
      {
        description = "Settings retrieved successfully"
        body<Map<String, String>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving settings"
        body<List<AppError>> {}
      }
  }
}
