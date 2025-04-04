package net.barrage.llmao.app.specialist.jirakira

import net.barrage.llmao.app.api.http.httpClient
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.specialist.SpecialistRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter

private const val JIRAKIRA_TOKEN_ORIGIN = "workflow.jirakira"

class JiraKiraWorkflowFactory(
  /** Jira endpoint. */
  private val endpoint: String,
  private val providers: ProviderState,
  private val settings: Settings,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
  val jiraKiraRepository: JiraKiraRepository,
  private val specialistRepositoryWrite: SpecialistRepositoryWrite,
) {
  suspend fun newJiraKiraWorkflow(
    user: User,
    emitter: Emitter<JiraKiraMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): JiraKiraWorkflow {
    val workflowId = KUUID.randomUUID()

    val userJiraApiKey =
      jiraKiraRepository.getUserApiKey(user.id)
        ?: throw AppError.api(ErrorReason.InvalidOperation, "No Jira API key found for user")

    val settings = settings.getAllWithDefaults()

    val jiraTimeSlotAttributeKey = settings.getOptional(SettingKey.JIRA_TIME_SLOT_ATTRIBUTE_KEY)

    val jiraKiraLlmProvider = settings[SettingKey.JIRA_KIRA_LLM_PROVIDER]
    val jiraKiraModel = settings[SettingKey.JIRA_KIRA_MODEL]

    if (!providers.llm.getProvider(jiraKiraLlmProvider).supportsModel(jiraKiraModel)) {
      throw AppError.api(
        ErrorReason.InvalidOperation,
        "JiraKira LLM provider does not support the configured JiraKira model",
      )
    }
    val jiraApi = JiraApi(endpoint = endpoint, apiKey = userJiraApiKey, client = httpClient())
    val jiraUser = jiraApi.getCurrentJiraUser()
    val worklogAttributes = jiraKiraRepository.listAllWorklogAttributes()

    return JiraKiraWorkflow(
      id = workflowId,
      user = user,
      jirakira =
        JiraKira(
          jiraApi = jiraApi,
          jiraUser = jiraUser,
          llm = providers.llm.getProvider(jiraKiraLlmProvider),
          tokenTracker =
            TokenUsageTracker(
              repository = tokenUsageRepositoryW,
              user = user,
              // TODO: FIX
              originType = JIRAKIRA_TOKEN_ORIGIN,
              originId = workflowId,
              agentId = null,
            ),
          model = jiraKiraModel,
          emitter = emitter,
          toolEmitter = toolEmitter,
          timeSlotAttributeKey = jiraTimeSlotAttributeKey,
          worklogAttributes = worklogAttributes,
          workflowId = workflowId,
          user = user,
          repository = jiraKiraRepository,
          specialistRepositoryWrite = specialistRepositoryWrite,
        ),
    )
  }
}
