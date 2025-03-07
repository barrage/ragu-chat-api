package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.app.api.http.queryParam
import net.barrage.llmao.core.models.AgentChatTimeSeries
import net.barrage.llmao.core.models.DashboardCounts
import net.barrage.llmao.core.models.common.Period
import net.barrage.llmao.core.services.AdministrationService
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
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

  get("/admin/dashboard/counts", dashboardCounts()) {
    val counts = service.dashboardCounts()
    call.respond(HttpStatusCode.OK, counts)
  }

  get("/admin/dashboard/chat/history", chatHistory()) {
    val period = call.queryParam("period")?.let(Period::valueOf) ?: Period.WEEK

    val history: AgentChatTimeSeries = service.getChatHistoryCountsByAgent(period)

    call.respond(HttpStatusCode.OK, history)
  }

  get("/admin/tokens/usage/total", tokenUsageForPeriod()) {
    val from = call.queryParam("from")?.let { KOffsetDateTime.parse(it) }
    val to = call.queryParam("to")?.let { KOffsetDateTime.parse(it) }
    val usage = service.getTotalTokenUsageForPeriod(from, to)
    call.respond(HttpStatusCode.OK, usage)
  }

  get("/admin/tokens/usage", listTokenUsage()) {
    val userId = call.queryParam("userId")?.let { KUUID.fromString(it) }
    val agentId = call.queryParam("agentId")?.let { KUUID.fromString(it) }
    val usage = service.listTokenUsage(userId = userId, agentId = agentId)
    call.respond(HttpStatusCode.OK, usage)
  }
}

private fun providers(): OpenApiRoute.() -> Unit = {
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

private fun providerModels(): OpenApiRoute.() -> Unit = {
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

private fun dashboardCounts(): OpenApiRoute.() -> Unit = {
  summary = "Get dashboard count statistics"
  description = "Get count statistics for the application dashboard."
  tags("admin/dashboard")
  response {
    HttpStatusCode.OK to
      {
        description = "Dashboard counts"
        body<DashboardCounts> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error"
        body<List<AppError>>()
      }
  }
}

private fun chatHistory(): OpenApiRoute.() -> Unit = {
  summary = "Get chat history"
  description = "Get chat history for the application dashboard."
  tags("admin/dashboard")
  request { queryParameter<Period>("period") { description = "Period (default week)" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Chat history"
        body<AgentChatTimeSeries> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error"
        body<List<AppError>>()
      }
  }
}

private fun tokenUsageForPeriod(): OpenApiRoute.() -> Unit = {
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

private fun listTokenUsage(): OpenApiRoute.() -> Unit = {
  summary = "List token usage"
  description = "List token usage for the application."
  tags("admin/tokens")
  request {
    queryParameter<KUUID>("userId") { description = "User ID" }
    queryParameter<KUUID>("agentId") { description = "Agent ID" }
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
