package net.barrage.llmao.app.workflow.jirakira

import io.ktor.util.logging.KtorSimpleLogger
import java.time.OffsetDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.ToolCallResult
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.ToolPropertyDefinition
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.tokens.TokenUsageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.workflow.jirakira.JiraKira")

class JiraKira(
  /** The workflow ID. */
  private val workflowId: KUUID,
  /** The user ID of the user who initiated the workflow. */
  private val userId: KUUID,
  private val jiraApi: JiraApi,
  private val jiraUser: JiraUser,
  private val llm: LlmProvider,
  private val tokenTracker: TokenUsageTracker,

  /** Which LLM to use. Has to support function calling. */
  private val model: String,

  /** Emits Jira related events such as worklog entry creation */
  private val emitter: Emitter<JiraKiraMessage>,

  /** Emits tool results. */
  private val toolEmitter: Emitter<ToolEvent>?,

  /**
   * The key of the time slot attribute to use when creating worklog entries. The key for this
   * attribute is set in Jira and must be configured into the app via the application settings.
   *
   * The value for this key is obtained per issue.
   */
  private val timeSlotAttributeKey: String,

  /**
   * A set of attributes that are allowed/required to be used in worklog entries. This is predefined
   * in jira and will be obtained via the Jira API when instantiating JiraKira. These attributes
   * will be included in the JSON schema for the [CreateWorklogEntrySchema] tool.
   */
  private val worklogAttributes: List<WorklogAttribute>,
  private val repository: JiraKiraRepository,
) {
  /** Lazily initialized when calling [completion] for the first time. */
  private lateinit var toolDefinitions: List<ToolDefinition>
  private val history: MutableList<ChatMessage> = mutableListOf(ChatMessage.system(context()))
  private var state: JiraKiraState = JiraKiraState.New

  suspend fun completion(
    message: String,
    useTools: Boolean = true,
    attempts: Int = 0,
    responseToMessageId: KUUID? = null,
  ) {
    if (state == JiraKiraState.New) {
      // Ensures tools are initialized
      loadTools()
      trackWorkflow()
      state = JiraKiraState.Persisted
    }

    val userMessageId = responseToMessageId ?: trackUserMessage(message)

    val completionResponse =
      llm.chatCompletion(
        history,
        ChatCompletionParameters(
          model = model,
          temperature = 0.1,
          presencePenalty = 0.0,
          tools = if (useTools) toolDefinitions else null,
          maxTokens = null,
        ),
      )

    completionResponse.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION,
        model = model,
        provider = llm.id(),
      )
    }

    val response = completionResponse.choices.first()

    if (response.message.toolCalls == null) {
      assert(response.message.content != null)

      trackAssistantMessage(userMessageId, response.message)

      emitter.emit(JiraKiraMessage.LlmResponse(response.message.content!!))

      return
    }

    assert(response.finishReason == FinishReason.ToolCalls)

    LOG.info(
      "{} - calling tools: {}",
      jiraUser.name,
      response.message.toolCalls.joinToString(", ") { it.name },
    )

    val toolResults = mutableListOf<ChatMessage>()

    for (toolCall in response.message.toolCalls) {
      when (toolCall.name) {
        TOOL_CREATE_WORKLOG_ENTRY -> {
          val input = Json.decodeFromString<CreateWorklogInput>(toolCall.arguments)

          val issueKey = jiraApi.getIssueKey(input.issueId)
          val timeslotAccount = jiraApi.getDefaultBillingAccountForIssue(issueKey.issueKey)

          LOG.debug("{} - creating worklog entry; input: {}", jiraUser.name, input)
          LOG.debug("{} - using timeslot account: {}", jiraUser.name, timeslotAccount)

          val worklog =
            jiraApi
              .createWorklogEntry(input, jiraUser.key, timeSlotAttributeKey, timeslotAccount?.key)
              .worklog

          emitter.emit(JiraKiraMessage.WorklogCreated(worklog))

          toolResults.add(
            ChatMessage.toolResult(
              "Success. ${worklog.issue.estimatedRemainingSeconds} estimate seconds remaining.",
              toolCall.id,
            )
          )
        }
        TOOL_LIST_USER_OPEN_ISSUES -> {
          val issues = jiraApi.getOpenIssuesForProject(Json.decodeFromString(toolCall.arguments))

          toolResults.add(
            ChatMessage.toolResult(
              "Found ${issues.issues.size} open issues for the user: ${issues.issues.joinToString("\n")}",
              toolCall.id,
            )
          )
        }
        TOOL_GET_ISSUE_ID -> {
          val input = Json.decodeFromString<JiraApi.IssueKey>(toolCall.arguments)
          val issueId = jiraApi.getIssueId(input.issueKey)

          toolResults.add(ChatMessage.toolResult(issueId, toolCall.id))
        }
        else -> {
          LOG.warn("Unknown tool call: {}", toolCall.name)
          return completion(message, attempts = attempts + 1, responseToMessageId = userMessageId)
        }
      }

      toolEmitter?.emit(
        ToolEvent.ToolResult(result = ToolCallResult(toolCall.id, "${toolCall.name}: success"))
      )
    }

    trackToolMessages(userMessageId, response.message, toolResults)

    LOG.debug("{} - rerunning completion (attempt: {})", jiraUser.name, attempts + 1)

    return completion(message, attempts = attempts + 1, responseToMessageId = userMessageId)
  }

  private suspend fun trackWorkflow() {
    repository.insertJiraKiraWorkflow(workflowId = workflowId, userId = userId)
  }

  private suspend fun trackUserMessage(prompt: String): KUUID {
    history.add(ChatMessage.user(prompt))
    return repository.insertJiraKiraMessage(
      JiraKiraMessageInsert(
        workflowId = workflowId,
        sender = userId,
        senderType = "user",
        content = prompt,
        toolCalls = null,
      )
    )
  }

  private suspend fun trackAssistantMessage(userMessageId: KUUID, message: ChatMessage) {
    history.add(message)
    repository.insertJiraKiraMessage(
      JiraKiraMessageInsert(
        workflowId = workflowId,
        sender = null,
        senderType = "assistant",
        content = message.content,
        responseTo = userMessageId,
        toolCalls = Json.encodeToString(message.toolCalls),
      )
    )
  }

  private suspend fun trackToolMessages(
    userMessageId: KUUID,
    assistantMessage: ChatMessage,
    messages: List<ChatMessage>,
  ) {
    history.add(assistantMessage)
    history.addAll(messages)
    repository.insertJiraKiraMessages(
      messages.map { message ->
        JiraKiraMessageInsert(
          workflowId = workflowId,
          sender = null,
          senderType = "tool",
          content = message.content,
          responseTo = userMessageId,
          toolCalls = Json.encodeToString(message.toolCalls),
        )
      }
    )
  }

  private suspend fun loadTools() {
    if (::toolDefinitions.isInitialized) {
      return
    }

    // Initialize the schema for the worklog entry tool
    // based on custom attributes from the repository
    // and the enumeration of their values obtained from the API

    val properties = CreateWorklogEntrySchema.function.parameters.properties.toMutableMap()

    val attributes = jiraApi.listWorklogAttributes()

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
      properties[attribute.key] = property
    }

    toolDefinitions =
      listOf(
        ListUserOpenIssuesSchema,
        GetIssueIdSchema,
        CreateWorklogEntrySchema.copy(
          function =
            CreateWorklogEntrySchema.function.copy(
              parameters =
                CreateWorklogEntrySchema.function.parameters.copy(properties = properties)
            )
        ),
      )
  }

  private fun context(): String {
    return """
        |$JIRA_KIRA_CONTEXT
        |The JIRA user you are talking to is called ${jiraUser.displayName} and their email is ${jiraUser.email}.
        |The user is logged in to Jira as ${jiraUser.name}. The user key to use in worklog entries is ${jiraUser.key}.
        |The time zone of the user is ${jiraUser.timeZone}. The current time is ${OffsetDateTime.now()}.
        """
      .trimMargin()
  }
}

const val JIRA_KIRA_CONTEXT =
  """
    |You are an expert in Jira. Your purpose is to help users manage their Jira tasks.
    |You have the capabilities of displaying and modifying Jira issues (tasks), including creating worklog entries.
    |Use the tools at your disposal to gather information about the user's Jira tasks and help them with them.
    |Never assume any parameters when calling tools. Always ask the user for them if you are uncertain.
    |The only time frame you can work in is the current week. Never assume any other time frame and reject any requests
    |that are not related to the current week.
"""

data class JiraKiraMessageInsert(
  val workflowId: KUUID,
  val sender: KUUID?,
  val senderType: String,
  val content: String?,
  val toolCalls: String?,
  val responseTo: KUUID? = null,
)

sealed class JiraKiraState {
  data object New : JiraKiraState()

  data object Persisted : JiraKiraState()
}
