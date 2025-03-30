package net.barrage.llmao.app.specialist.jirakira

import io.ktor.util.logging.KtorSimpleLogger
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolCallResult
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.ToolPropertyDefinition
import net.barrage.llmao.core.model.SpecialistMessageInsert
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.specialist.SpecialistRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.workflow.jirakira.JiraKira")

class JiraKira(
  /** The workflow ID. */
  private val workflowId: KUUID,
  /** The user ID of the user who initiated the workflow. */
  private val user: User,
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
   * If not present, the time slot will not be present on created worklogs. The value for this key
   * is obtained per issue.
   */
  private val timeSlotAttributeKey: String?,

  /**
   * A set of attributes that are allowed/required to be used in worklog entries. This is predefined
   * in jira and will be obtained via the Jira API when instantiating JiraKira. These attributes
   * will be included in the JSON schema for the [CreateWorklogEntrySchema] tool.
   */
  private val worklogAttributes: List<WorklogAttribute>,
  private val repository: JiraKiraRepository,
  private val specialistRepositoryWrite: SpecialistRepositoryWrite,
) {
  /** Lazily initialized when calling [completion] for the first time. */
  private lateinit var toolDefinitions: List<ToolDefinition>

  /** The message history that always starts with the context. */
  private val history: MutableList<ChatMessage> = mutableListOf(ChatMessage.system(context()))

  /** Current Jira Kira state. */
  private var state: JiraKiraState = JiraKiraState.New

  /** Used to prevent Jira Kira inputs when it is already doing work. */
  private var lock: Boolean = false

  suspend fun emitError(e: AppError) {
    emitter.emitError(e)
  }

  suspend fun execute(message: String) {
    if (lock) {
      throw AppError.api(ErrorReason.Websocket, "Jira Kira is busy")
    }

    try {
      if (state == JiraKiraState.New) {
        // Ensures tools are initialized
        loadTools()
        trackWorkflow()
        state = JiraKiraState.Persisted
      }

      val buffer = mutableListOf<ChatMessage>(ChatMessage.user(message))

      lock = true

      completion(messageBuffer = buffer)
      storePendingBuffer(buffer)

      // Track the messages only if the above 2 calls succeed
      // to try to keep it as consistent as possible.
      history.addAll(buffer)

      lock = false
    } catch (e: Throwable) {
      lock = false
      throw e
    }
  }

  /**
   * Run completion and handle tool calls.
   *
   * @param messageBuffer The message buffer for tracking messages. Has to contain the user message
   *   initially.
   * @param attempt Current attempt. Used to prevent infinite loops.
   * @param maxAttempts Safeguard for infinite recursion in case the agent keeps calling tools.
   */
  private suspend fun completion(
    /**
     * A buffer that keeps track of new messages that are not yet persisted. Since we call this
     * recursively, but don't want to persist prompts that fail, this needs to be passed as an
     * argument.
     */
    messageBuffer: MutableList<ChatMessage>,
    attempt: Int = 0,
    maxAttempts: Int = 3,
  ) {
    LOG.debug("{} - running completion (attempt: {})", jiraUser.name, attempt + 1)

    val completionResponse =
      llm.chatCompletion(
        // Append the message buffer to the history
        history + messageBuffer,
        ChatCompletionParameters(
          model = model,
          temperature = 0.1,
          presencePenalty = 0.0,
          tools = if (attempt < maxAttempts) toolDefinitions else null,
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

    // Track the assistant's message
    messageBuffer.add(response.message)

    response.message.content?.let { content ->
      if (content.text().isNotBlank()) {
        emitter.emit(JiraKiraMessage.LlmResponse(content.text()))
      }
    }

    if (response.message.toolCalls == null) {
      // This is the end of the interaction
      assert(response.finishReason == FinishReason.Stop)
      return
    }

    // From this point on we are handling tool calls
    // and need to call completion again

    assert(response.finishReason == FinishReason.ToolCalls)

    LOG.info(
      "{} - calling tools: {}",
      jiraUser.name,
      response.message.toolCalls.joinToString(", ") { it.name },
    )

    for (toolCall in response.message.toolCalls) {
      // Track all tool calls
      handleToolCall(toolCall).let { messageBuffer.add(it) }
    }

    return completion(
      attempt = attempt + 1,
      maxAttempts = maxAttempts,
      messageBuffer = messageBuffer,
    )
  }

  /** Tool calling dirty work. Always returns a chat message with role set to "tool". */
  private suspend fun handleToolCall(toolCall: ToolCallData): ChatMessage {
    try {
      toolEmitter?.emit(ToolEvent.ToolCall(toolCall))

      val result =
        when (toolCall.name) {
          TOOL_CREATE_WORKLOG_ENTRY -> {
            LOG.debug("{} - creating worklog entry; input: {}", jiraUser.name, toolCall.arguments)
            val input = Json.decodeFromString<CreateWorklogInput>(toolCall.arguments)

            val issueKey = jiraApi.getIssueKey(input.issueId)

            val timeSlotAccount =
              timeSlotAttributeKey?.let {
                val acc =
                  jiraApi.getDefaultBillingAccountForIssue(issueKey.issueKey) ?: return@let null
                val timeSlotAccount = TimeSlotAttribute(timeSlotAttributeKey, acc.key)
                LOG.debug("{} - using timeslot account: {}", jiraUser.name, timeSlotAccount)
                timeSlotAccount
              }

            val worklog = jiraApi.createWorklogEntry(input, jiraUser.key, timeSlotAccount)

            LOG.debug(
              "Worklog for issue ${input.issueId} created successfully for issue (time: {})",
              worklog.timeSpent,
            )

            emitter.emit(JiraKiraMessage.WorklogCreated(worklog))

            ChatMessage.toolResult(
              "Success. Worklog entry ID: ${worklog.tempoWorklogId}.",
              toolCall.id,
            )
          }

          TOOL_LIST_USER_OPEN_ISSUES -> {
            LOG.debug("{} - listing open issues; input: {}", jiraUser.name, toolCall.arguments)

            val input = Json.decodeFromString<ProjectKey>(toolCall.arguments)

            val issues = jiraApi.getOpenIssuesForProject(input.project)

            return ChatMessage.toolResult(issues.joinToString("\n"), toolCall.id)
          }

          TOOL_GET_ISSUE_ID -> {
            LOG.debug("{} - getting ID for issue; input: {}", jiraUser.name, toolCall.arguments)
            val input = Json.decodeFromString<IssueKey>(toolCall.arguments)

            val issueId = jiraApi.getIssueId(input.issueKey)

            ChatMessage.toolResult(issueId, toolCall.id)
          }

          TOOL_UPDATE_WORKLOG_ENTRY -> {
            LOG.debug("{} - updating worklog entry; input: {}", jiraUser.name, toolCall.arguments)

            val input =
              try {
                Json.decodeFromString<UpdateWorklogInput>(toolCall.arguments)
              } catch (e: SerializationException) {
                LOG.error("Failed to decode tool call arguments: {}", toolCall.arguments, e)
                if (e.message?.startsWith("Missing required field") == true) {
                  return ChatMessage.toolResult("${e.message}", toolCall.id)
                }
                throw e
              }

            val worklog = jiraApi.updateWorklogEntry(input)

            LOG.debug(
              "Worklog for issue ${worklog.issue?.key} updated successfully (time: {})",
              worklog.timeSpent,
            )

            emitter.emit(JiraKiraMessage.WorklogUpdated(worklog))

            ChatMessage.toolResult("success", toolCall.id)
          }

          TOOL_GET_ISSUE_WORKLOG -> {
            LOG.debug("{} - getting issue worklog; input: {}", jiraUser.name, toolCall.arguments)

            val input = Json.decodeFromString<IssueKeyOrId>(toolCall.arguments)

            val worklog = jiraApi.getIssueWorklog(input.issueKeyOrId, jiraUser.key)

            val result = worklog.joinToString("\n")

            LOG.debug("{} - worklog: {}", jiraUser.name, result)

            ChatMessage.toolResult(result, toolCall.id)
          }

          else -> {
            LOG.warn("{} - received unknown tool call: {}", jiraUser.name, toolCall.name)
            return ChatMessage.toolResult("error: unknown tool", toolCall.id)
          }
        }

      toolEmitter?.emit(
        ToolEvent.ToolResult(result = ToolCallResult(toolCall.id, "${toolCall.name}: success"))
      )

      return result
    } catch (e: JiraError) {
      LOG.error("Jira API error:", e)

      toolEmitter?.emit(
        ToolEvent.ToolResult(result = ToolCallResult(toolCall.id, "Error: ${e.message}"))
      )

      return ChatMessage.toolResult("error: ${e.message}", toolCall.id)
    } catch (e: Throwable) {
      LOG.error("Error in tool call", e)

      toolEmitter?.emit(
        ToolEvent.ToolResult(result = ToolCallResult(toolCall.id, "Error: ${e.message}"))
      )

      return ChatMessage.toolResult("error: ${e.message}", toolCall.id)
    }
  }

  private suspend fun trackWorkflow() {
    specialistRepositoryWrite.insertWorkflow(
      workflowId = workflowId,
      userId = user.id,
      username = user.username,
    )
  }

  private suspend fun storePendingBuffer(buffer: MutableList<ChatMessage>) {
    specialistRepositoryWrite.insertMessages(
      workflowId = workflowId,
      messages =
        buffer.map { message ->
          SpecialistMessageInsert(
            senderType = message.role,
            content = message.content?.text(),
            toolCalls = Json.encodeToString(message.toolCalls),
            toolCallId = message.toolCallId,
            finishReason = message.finishReason,
          )
        },
    )
  }

  /**
   * Load tool JSON schema definitions into [toolDefinitions].
   *
   * This method will load all custom worklog attributes from the Jira API and include them in the
   * tool definitions.
   *
   * All attributes are obtained from the Jira API and are matched against the ones defined in the
   * database. Only those that are present in the database will be included in the tool definitions.
   */
  private suspend fun loadTools() {
    if (::toolDefinitions.isInitialized) {
      return
    }

    // Initialize the schema for the worklog entry tool
    // based on custom attributes from the repository
    // and the enumeration of their values obtained from the API

    val propertiesCreateWorklog =
      CreateWorklogEntrySchema.function.parameters.properties.toMutableMap()
    val propertiesUpdateWorklog =
      UpdateWorklogEntrySchema.function.parameters.properties.toMutableMap()

    val requiredAttributes = worklogAttributes.filter { it.required }.map { it.key }

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
      propertiesCreateWorklog[attribute.key] = property
      propertiesUpdateWorklog[attribute.key] = property
    }

    toolDefinitions =
      listOf(
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

  private fun context(): String {
    return """
        |$JIRA_KIRA_CONTEXT
        |The JIRA user you are talking to is called ${jiraUser.displayName} and their email is ${jiraUser.email}.
        |The user is logged in to Jira as ${jiraUser.name} and their Jira user key is ${jiraUser.key}.
        |The time zone of the user is ${jiraUser.timeZone}. The current time is ${OffsetDateTime.now()}.
        """
      .trimMargin()
  }
}

const val JIRA_KIRA_CONTEXT =
  """
    |You are an expert in Jira. Your purpose is to help users manage their Jira tasks.
    |Use the tools at your disposal to gather information about the user's Jira tasks and help them with them.
    |Never assume any parameters when calling tools. Always ask the user for them if you are uncertain.
    |The only time frame you can work in is the current week. Never assume any other time frame and reject any requests
    |that are not related to the current week.
"""

/** Input for the [TOOL_GET_ISSUE_ID] tool. */
@Serializable data class IssueKey(val issueKey: String)

@Serializable data class IssueKeyOrId(val issueKeyOrId: String)

@Serializable data class ProjectKey(val project: String)

sealed class JiraKiraState {
  data object New : JiraKiraState()

  data object Persisted : JiraKiraState()
}
