package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import net.barrage.llmao.app.AdapterState
import net.barrage.llmao.app.workflow.jirakira.JiraKiraWorkflowFactory
import net.barrage.llmao.core.AppError

private const val JIRA_KIRA_NAME = "Gojira"

fun Route.specialistWorkflowRoutes(adapterState: AdapterState) {
  get("/workflows", listSpecialists()) {
    val specialists = mutableListOf<SpecialistAgent>()

    adapterState.runIfEnabled<JiraKiraWorkflowFactory, Unit> {
      specialists.add(
        SpecialistAgent(name = JIRA_KIRA_NAME, description = "Be a Jira hero.", workflowTypeId = "")
      )

      call.respond(specialists)
    }
  }
}

@Serializable
data class SpecialistAgent(val name: String, val description: String, val workflowTypeId: String)

private fun listSpecialists(): RouteConfig.() -> Unit = {
  tags("agents/specialists")
  description = "List all available specialist agents"
  response {
    HttpStatusCode.OK to
      {
        description = "List of all available specialist agents"
        body<List<SpecialistAgent>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving specialist agents"
        body<List<AppError>> {}
      }
  }
}
