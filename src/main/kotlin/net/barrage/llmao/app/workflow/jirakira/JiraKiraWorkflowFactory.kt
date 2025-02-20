package net.barrage.llmao.app.workflow.jirakira

import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.httpClient
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.SettingsService
import net.barrage.llmao.core.tokens.TokenUsageRepositoryWrite
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private const val JIRAKIRA_TOKEN_ORIGIN = "workflow.jirakira"

class JiraKiraWorkflowFactory(
  private val providers: ProviderState,
  private val settingsService: SettingsService,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
  val jiraKiraRepository: JiraKiraRepository,
) {
  suspend fun newJiraKiraWorkflow(
    userId: KUUID,
    emitter: Emitter<JiraKiraMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): JiraKiraWorkflow {
    val workflowId = KUUID.randomUUID()

    val userJiraApiKey =
      jiraKiraRepository.getUserApiKey(userId) ?: throw Exception("No Jira API key found for user")

    val settings = settingsService.getAllWithDefaults()

    val jiraEndpoint =
      settings.getOptional(SettingKey.JIRA_ENDPOINT)
        ?: throw AppError.api(ErrorReason.InvalidOperation, "Jira endpoint not configured")

    val jiraTimeSlotAttributeKey =
      settings.getOptional(SettingKey.JIRA_TIME_SLOT_ATTRIBUTE_KEY)
        ?: throw AppError.api(
          ErrorReason.InvalidOperation,
          "Jira time slot attribute key not configured",
        )

    val jiraKiraLlmProvider = settings[SettingKey.JIRA_KIRA_LLM_PROVIDER]
    val jiraKiraModel = settings[SettingKey.JIRA_KIRA_MODEL]

    if (!providers.llm.getProvider(jiraKiraLlmProvider).supportsModel(jiraKiraModel)) {
      throw AppError.api(
        ErrorReason.InvalidOperation,
        "JiraKira LLM provider does not support the configured JiraKira model",
      )
    }
    val jiraApi = JiraApi(endpoint = jiraEndpoint, apiKey = userJiraApiKey, client = httpClient())
    val jiraUser = jiraApi.getCurrentJiraUser()
    val worklogAttributes = jiraKiraRepository.listAllWorklogAttributes()

    return JiraKiraWorkflow(
      id = workflowId,
      userId = userId,
      jirakira =
        JiraKira(
          jiraApi = jiraApi,
          jiraUser = jiraUser,
          llm = providers.llm.getProvider(jiraKiraLlmProvider),
          tokenTracker =
            TokenUsageTracker(
              repository = tokenUsageRepositoryW,
              userId = userId,
              agentId = null,
              agentConfigurationId = null,
              origin = JIRAKIRA_TOKEN_ORIGIN,
              originId = workflowId,
            ),
          model = jiraKiraModel,
          emitter = emitter,
          toolEmitter = toolEmitter,
          timeSlotAttributeKey = jiraTimeSlotAttributeKey,
          worklogAttributes = worklogAttributes,
          workflowId = workflowId,
          userId = userId,
          repository = jiraKiraRepository,
        ),
    )
  }
}
