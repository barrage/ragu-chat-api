package net.barrage.llmao.app.administration

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import net.barrage.llmao.app.http.query
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.PluginConfiguration
import net.barrage.llmao.core.ProvidersResponse
import net.barrage.llmao.core.administration.Administration
import net.barrage.llmao.core.token.TokenUsageAggregate
import net.barrage.llmao.core.token.TokenUsageListParameters
import net.barrage.llmao.types.KLocalDate
import net.barrage.llmao.types.KOffsetDateTime

fun Route.administrationRoutes() {
  get("/admin/providers", providers()) {
    val providers = Administration.listProviders()
    call.respond(HttpStatusCode.OK, providers)
  }

  get("/admin/providers/llm/{provider}", providerModels()) {
    val provider = call.parameters["provider"]!!
    val models = Administration.listLanguageModels(provider)
    call.respond(HttpStatusCode.OK, models)
  }

  get("/admin/tokens/usage", aggregateTokenUsage()) {
    val params = call.query(TokenUsageListParameters::class)
    val usage = Administration.aggregateTokenUsage(params)
    call.respond(HttpStatusCode.OK, usage)
  }
}

fun Route.applicationInfoRoutes() {
  get("/plugins", applicationInfo()) {
    val info = Administration.listPlugins()
    call.respond(HttpStatusCode.OK, info)
  }
}

private fun applicationInfo(): RouteConfig.() -> Unit = {
  summary = "Get application info"
  description = "Get application info."
  tags("admin/info")
  securitySchemeNames = listOf()
  response {
    HttpStatusCode.OK to
      {
        description = "Application info"
        body<List<PluginConfiguration>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error"
        body<List<AppError>>()
      }
  }
}

private fun providers(): RouteConfig.() -> Unit = {
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
              llm = listOf("openai"),
              vector = listOf("weaviate"),
              embedding = listOf("azure"),
              image = "minio",
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

private fun providerModels(): RouteConfig.() -> Unit = {
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

private fun tokenUsageForPeriod(): RouteConfig.() -> Unit = {
  summary = "Get the total token usage for a given period."
  description = "Get token usage."
  tags("admin/tokens")
  request {
    queryParameter<KOffsetDateTime>("from") { description = "From date" }
    queryParameter<KOffsetDateTime>("to") { description = "To date" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Token usage"
        body<List<Number>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error"
        body<List<AppError>>()
      }
  }
}

private fun aggregateTokenUsage(): RouteConfig.() -> Unit = {
  summary = "List token usage"
  description = "List token usage for the application."
  tags("admin/tokens")
  request {
    queryParameter<String?>("userId") { description = "User ID" }
    queryParameter<String?>("workflowType") { description = "Workflow type" }
    queryParameter<KLocalDate?>("from") { description = "From date" }
    queryParameter<KLocalDate?>("to") { description = "To date" }
    queryParameter<Int?>("limit") { description = "Limit" }
    queryParameter<Int?>("offset") { description = "Offset" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Token usage"
        body<TokenUsageAggregate> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error"
        body<List<AppError>>()
      }
  }
}
