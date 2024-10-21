package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.core.services.AdministrationService
import net.barrage.llmao.error.AppError

fun Route.administrationRouter(service: AdministrationService) {
  get("/admin/providers", providers()) {
    val providers = service.listProviders()
    call.respond(HttpStatusCode.OK, providers)
  }

  get("/admin/providers/llm/{provider}", providerModels()) {
    // Safe to yell since the route won't match if there's no provider
    val provider = call.parameters["provider"]!!
    val models = service.listLanguageModels(provider)
    call.respond(HttpStatusCode.OK, models)
  }
}

fun providers(): OpenApiRoute.() -> Unit = {
  summary = "List all available providers"
  description = "List all available providers for the application."
  tags("providers")
  securitySchemeNames = listOf()
  response {
    HttpStatusCode.OK to
      {
        description = "List of providers"
        body<ProvidersResponse> {
          example("example") {
            ProvidersResponse(
              auth = listOf("google"),
              llm = listOf("openai"),
              vector = listOf("weaviate"),
              embedding = listOf("azure"),
            )
          }
        }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error"
        body<List<AppError>>()
      }
  }
}

fun providerModels(): OpenApiRoute.() -> Unit = {
  summary = "List all available language models for a provider"
  description = "List all available language models for a provider."
  tags("providers")
  securitySchemeNames = listOf()
  request { pathParameter<String>("provider") { description = "Provider name" } }
  response {
    HttpStatusCode.OK to
      {
        description = "List of language models"
        body<List<String>> { example("example") { value = listOf("gpt-3.5-turbo", "gpt-4") } }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error"
        body<List<AppError>>()
      }
  }
}
