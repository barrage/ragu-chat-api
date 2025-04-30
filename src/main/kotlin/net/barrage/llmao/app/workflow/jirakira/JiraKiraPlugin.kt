package net.barrage.llmao.app.workflow.jirakira

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.Route
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.WorkflowFactoryManager

const val JIRAKIRA_WORKFLOW_ID = "JIRAKIRA"

class JiraKiraPlugin : Plugin {
  private lateinit var repository: JiraKiraRepository

  override fun id(): String = JIRAKIRA_WORKFLOW_ID

  override suspend fun configureState(config: ApplicationConfig, state: ApplicationState) {
    repository = JiraKiraRepository(state.database)
    JiraKiraWorkflowFactory.init(config, state)
    WorkflowFactoryManager.register(JiraKiraWorkflowFactory)
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    authenticate("admin") { jiraKiraAdminRoutes(repository) }
    authenticate("user") { jiraKiraUserRoutes(repository) }
  }
}
