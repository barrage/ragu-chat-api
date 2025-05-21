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

  override suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
    repository = JiraKiraRepository(state.database)
    JiraKiraWorkflowFactory.init(config, state)
    WorkflowFactoryManager.register(JiraKiraWorkflowFactory)
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    authenticate("admin") { jiraKiraAdminRoutes(repository) }
    authenticate("user") { jiraKiraUserRoutes(repository) }
  }
}

/** The LLM provider to use for JiraKira. */
internal data object JiraKiraLlmProvider {
  const val KEY = "JIRAKIRA_LLM_PROVIDER"
}

/** Which model will be used for JiraKira. Has to be compatible with [JiraKiraLlmProvider]. */
internal data object JiraKiraModel {
  const val KEY = "JIRAKIRA_MODEL"
}

/**
 * The attribute to use as the time slot attribute when creating worklog entries with the Jira API.
 * Defined in Jira.
 */
internal data object JiraKiraTimeSlotAttributeKey {
  const val KEY = "JIRAKIRA_TIME_SLOT_ATTRIBUTE_KEY"
}

internal data object JiraKiraMaxHistoryTokens {
  const val KEY = "JIRAKIRA_MAX_HISTORY_TOKENS"
  const val DEFAULT = 100_000
}
