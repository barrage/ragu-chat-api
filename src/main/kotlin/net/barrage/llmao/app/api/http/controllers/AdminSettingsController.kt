package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.api.admin.AdminSettingsService
import net.barrage.llmao.core.model.SettingsUpdate

fun Route.adminSettingsRoutes(settingsService: AdminSettingsService) {
  route("/admin/settings") {
    put(updateSettings()) {
      val parameters: SettingsUpdate = call.receive()
      settingsService.update(parameters)
      call.respond(HttpStatusCode.NoContent)
    }

    get(getSettings()) {
      val settings = settingsService.getAllWithDefaults()
      call.respond(HttpStatusCode.OK, settings)
    }
  }
}

private fun updateSettings(): RouteConfig.() -> Unit = {
  tags("admin/settings")
  description = "Update settings"
  request { body<SettingsUpdate> { description = "Updated settings" } }
  response {
    HttpStatusCode.NoContent to { description = "Settings updated successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating settings"
        body<List<AppError>> {}
      }
  }
}

private fun getSettings(): RouteConfig.() -> Unit = {
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
