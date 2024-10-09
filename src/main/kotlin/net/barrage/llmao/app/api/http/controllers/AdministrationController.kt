package net.barrage.llmao.app.api.http.controllers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.services.AdministrationService

fun Route.administrationRouter(service: AdministrationService) {
  get("/admin/providers") {
    val models = service.listProviders()
    call.respond(HttpStatusCode.OK, models)
  }

  get("/admin/providers/llm/{provider}") {
    // Safe to yell since the route won't match if there's no provider
    val provider = call.parameters["provider"]!!
    val models = service.listLanguageModels(provider)
    call.respond(HttpStatusCode.OK, models)
  }
}
