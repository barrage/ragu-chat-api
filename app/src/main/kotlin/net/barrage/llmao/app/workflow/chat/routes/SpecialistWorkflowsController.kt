package net.barrage.llmao.app.workflow.chat.routes

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.http.user

private const val JIRA_KIRA_NAME = "Gojira"

fun Route.specialistWorkflowRoutes() {
  get("/agents/specialists", listSpecialists()) {
    mutableListOf<SpecialistAgent>()
    call.user()

    // A user needs to have a Jira API key to use JiraKira
    //    Plugins.runIfEnabled<JiraKiraWorkflowFactory> { factory ->
    //      if (factory.jiraKiraRepository.getUserApiKey(user.id) == null) {
    //        return@runIfEnabled
    //      }
    //
    //      specialists.add(
    //        SpecialistAgent(
    //          name = JIRA_KIRA_NAME,
    //          description = "The hero Jira needs, but does not deserve.",
    //          workflowTypeId = JIRAKIRA_WORKFLOW_ID,
    //        )
    //      )
    //
    //      call.respond(specialists)
    //    }
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
