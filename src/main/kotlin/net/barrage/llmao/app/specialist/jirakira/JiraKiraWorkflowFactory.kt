package net.barrage.llmao.app.specialist.jirakira

import com.knuddels.jtokkit.api.EncodingRegistry
import net.barrage.llmao.app.api.http.httpClient
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.chat.ConversationWorkflow
import net.barrage.llmao.core.chat.MessageBasedHistory
import net.barrage.llmao.core.chat.TokenBasedHistory
import net.barrage.llmao.core.chat.WorkflowFactory
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolPropertyDefinition
import net.barrage.llmao.core.llm.ToolchainBuilder
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.specialist.SpecialistRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow

private const val JIRAKIRA_TOKEN_ORIGIN = "workflow.jirakira"
private const val JIRA_KIRA_WORKFLOW_ID = "JIRAKIRA"

class JiraKiraWorkflowFactory(
  /** Jira endpoint. */
  private val endpoint: String,
  private val providers: ProviderState,
  private val settings: Settings,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
  val jiraKiraRepository: JiraKiraRepository,
  private val specialistRepositoryWrite: SpecialistRepositoryWrite,
  private val messageProcessor: ChatMessageProcessor,
  private val encodingRegistry: EncodingRegistry,
) : WorkflowFactory {
  override fun type(): String = JIRA_KIRA_WORKFLOW_ID

  override suspend fun new(user: User, agentId: String?, emitter: Emitter): ConversationWorkflow {
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

    val tools = loadTools(jiraApi, worklogAttributes)

    val state =
      JiraKiraState(
        jiraUser = jiraUser,
        timeSlotAttributeKey = jiraTimeSlotAttributeKey,
        emitter = emitter,
        api = jiraApi,
      )

    val builder = ToolchainBuilder<JiraKiraState>()
    for (tool in tools) {
      val fn =
        when (tool.function.name) {
          TOOL_LIST_USER_OPEN_ISSUES -> ::listUserOpenIssues
          TOOL_GET_ISSUE_ID -> ::getIssueId
          TOOL_GET_ISSUE_WORKLOG -> ::getIssueWorklog
          TOOL_CREATE_WORKLOG_ENTRY -> ::createWorklogEntry
          TOOL_UPDATE_WORKLOG_ENTRY -> ::updateWorklogEntry
          else -> throw AppError.internal("Unknown tool: ${tool.function.name}")
        }
      builder.addTool(definition = tool, handler = fn)
    }

    val toolchain = builder.build(state = state, emitter = emitter)

    val tokenizer =
      encodingRegistry.getEncodingForModel(jiraKiraModel).let { if (it.isEmpty) null else it.get() }

    val tokenTracker =
      TokenUsageTracker(
        repository = tokenUsageRepositoryW,
        user = user,
        originType = JIRAKIRA_TOKEN_ORIGIN,
        originId = workflowId,
        agentId = null,
      )

    return JiraKiraWorkflow(
      id = workflowId,
      user = user,
      agent =
        JiraKira(
          llmProvider = providers.llm.getProvider(jiraKiraLlmProvider),
          tokenTracker = tokenTracker,
          model = jiraKiraModel,
          emitter = emitter,
          user = user,
          toolchain = toolchain,
          history =
            tokenizer?.let {
              TokenBasedHistory(
                messages = mutableListOf(),
                tokenizer = it,
                maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
              )
            } ?: MessageBasedHistory(messages = mutableListOf(), maxMessages = 20),
          jiraUser = jiraUser,
          messageProcessor = messageProcessor,
        ),
      emitter = emitter,
      messageProcessor = messageProcessor,
      repository = specialistRepositoryWrite,
    )
  }

  override suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow {
    TODO("Not yet implemented")
  }

  /**
   * Load tool JSON schema definitions based on custom worklog attributes.
   *
   * This method will load all custom worklog attributes from the Jira API and include them in the
   * tool definitions.
   *
   * All attributes are obtained from the Jira API and are matched against the ones defined in the
   * database. Only those that are present in the database will be included in the tool definitions.
   */
  private suspend fun loadTools(
    api: JiraApi,

    /**
     * A set of attributes that are allowed/required to be used in worklog entries. This is
     * predefined in jira and will be obtained via the Jira API when instantiating JiraKira. These
     * attributes will be included in the JSON schema for the [CreateWorklogEntrySchema] tool.
     */
    worklogAttributes: List<WorklogAttribute>,
  ): List<ToolDefinition> {

    // Initialize the schema for the worklog entry tool
    // based on custom attributes from the repository
    // and the enumeration of their values obtained from the API

    val propertiesCreateWorklog =
      CreateWorklogEntrySchema.function.parameters.properties.toMutableMap()
    val propertiesUpdateWorklog =
      UpdateWorklogEntrySchema.function.parameters.properties.toMutableMap()

    val requiredAttributes = worklogAttributes.filter { it.required }.map { it.key }

    val attributes = api.listWorklogAttributes()

    for (attribute in attributes) {
      val worklogAttribute = worklogAttributes.find { it.key == attribute.key }

      if (worklogAttribute == null) {
        LOG.debug("Skipping worklog attribute '{}'", attribute.name)
        continue
      }

      if (attribute.staticListValues == null) {
        LOG.warn(
          "Worklog attribute '{}' ({}) has no static list values, only static list attributes are supported",
          attribute.name,
          attribute.key,
        )
        continue
      }

      val enumerations = attribute.staticListValues.map { it.value }
      val property =
        ToolPropertyDefinition(
          type = "string",
          description = worklogAttribute.description,
          enum = enumerations,
        )

      LOG.debug("Adding worklog attribute to tool definition: {}", attribute.key)
      propertiesCreateWorklog[attribute.key] = property
      propertiesUpdateWorklog[attribute.key] = property
    }

    return listOf(
      ListUserOpenIssuesSchema,
      GetIssueIdSchema,
      GetIssueWorklogSchema,
      UpdateWorklogEntrySchema.copy(
        function =
          UpdateWorklogEntrySchema.function.copy(
            parameters =
              UpdateWorklogEntrySchema.function.parameters.copy(
                properties = propertiesUpdateWorklog
              )
          )
      ),
      CreateWorklogEntrySchema.copy(
        function =
          CreateWorklogEntrySchema.function.copy(
            parameters =
              CreateWorklogEntrySchema.function.parameters.copy(
                properties = propertiesCreateWorklog,
                required =
                  CreateWorklogEntrySchema.function.parameters.required + requiredAttributes,
              )
          )
      ),
    )
  }
}
