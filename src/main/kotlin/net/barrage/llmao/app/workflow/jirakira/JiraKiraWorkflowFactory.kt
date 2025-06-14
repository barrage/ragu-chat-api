package net.barrage.llmao.app.workflow.jirakira

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.http.httpClient
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.llm.TokenBasedHistory
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.string

object JiraKiraWorkflowFactory : WorkflowFactory {
  /** Jira endpoint. */
  private lateinit var endpoint: String
  private lateinit var providers: ProviderState
  private lateinit var settings: Settings
  private lateinit var jiraKiraRepository: JiraKiraRepository

  fun init(config: ApplicationConfig, state: ApplicationState) {
    endpoint = config.string("jirakira.endpoint")
    providers = state.providers
    settings = state.settings
    jiraKiraRepository = JiraKiraRepository(state.database)
  }

  override fun id(): String = JIRAKIRA_WORKFLOW_ID

  override suspend fun new(user: User, emitter: Emitter, params: JsonElement?): Workflow {
    val workflowId = KUUID.randomUUID()

    val userJiraApiKey =
      jiraKiraRepository.getUserApiKey(user.id)
        ?: throw AppError.api(ErrorReason.InvalidOperation, "No Jira API key found for user")

    val settings = settings.getAll()

    val jiraTimeSlotAttributeKey = settings.getOptional(JiraKiraTimeSlotAttributeKey.KEY)
    val jiraKiraLlmProvider = settings[JiraKiraLlmProvider.KEY]
    val jiraKiraModel = settings[JiraKiraModel.KEY]

    if (!providers.llm[jiraKiraLlmProvider].supportsModel(jiraKiraModel)) {
      throw AppError.api(
        ErrorReason.InvalidOperation,
        "JiraKira LLM provider does not support the configured JiraKira model",
      )
    }
    val jiraApi = JiraApi(endpoint = endpoint, apiKey = userJiraApiKey, client = httpClient())
    val jiraUser = jiraApi.getCurrentJiraUser()

    val tempoWorklogAttributes = jiraApi.listWorklogAttributes()
    val worklogAttributes = jiraKiraRepository.listAllWorklogAttributes()

    val state =
      JiraKiraToolExecutor(
        jiraUser = jiraUser,
        timeSlotAttributeKey = jiraTimeSlotAttributeKey,
        emitter = emitter,
        api = jiraApi,
      )

    val tools = state.tools(tempoWorklogAttributes, worklogAttributes)

    val tokenizer = Encoder.tokenizer(jiraKiraModel)

    val tokenTracker =
      TokenUsageTrackerFactory.newTracker(user.id, user.username, JIRAKIRA_WORKFLOW_ID, workflowId)

    return JiraKiraWorkflow(
      id = workflowId,
      user = user,
      tools = tools,
      emitter = emitter,
      repository = jiraKiraRepository,
      agent =
        JiraKira(
          inferenceProvider = providers.llm[jiraKiraLlmProvider],
          tokenTracker = tokenTracker,
          model = jiraKiraModel,
          history =
            tokenizer?.let {
              TokenBasedHistory(
                messages = mutableListOf(),
                tokenizer = it,
                maxTokens =
                  settings.getOptional(JiraKiraMaxHistoryTokens.KEY)?.toInt()
                    ?: JiraKiraMaxHistoryTokens.DEFAULT,
              )
            } ?: MessageBasedHistory(messages = mutableListOf(), maxMessages = 20),
        ),
      jiraUser = jiraUser,
    )
  }

  override suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow {
    TODO("Not yet implemented")
  }
}
