package net.barrage.llmao.app.workflow.jirakira

import io.ktor.util.logging.KtorSimpleLogger
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.chat.ChatHistory
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.ToolCallResult
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.WorkflowAgent

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.workflow.jirakira.JiraKira")

data class JiraKiraState(
  val emitter: Emitter,
  val jiraUser: JiraUser,
  val api: JiraApi,

  /**
   * The key of the time slot attribute to use when creating worklog entries. The key for this
   * attribute is set in Jira and must be configured into the app via the application settings.
   *
   * If not present, the time slot will not be present on created worklogs. The value for this key
   * is obtained per issue.
   */
  val timeSlotAttributeKey: String?,
)

class JiraKira(
  private val jiraUser: JiraUser,
  private val emitter: Emitter,
  toolchain: Toolchain<JiraKiraState>,
  messageProcessor: ChatMessageProcessor,
  user: User,
  tokenTracker: TokenUsageTracker,
  history: ChatHistory,
  llmProvider: LlmProvider,
  model: String,
) :
  WorkflowAgent<JiraKiraState>(
    user = user,
    model = model,
    llmProvider = llmProvider,
    tokenTracker = tokenTracker,
    contextEnrichment = null,
    history = history,
    messageProcessor = messageProcessor,
    toolchain = toolchain,
    completionParameters =
      ChatCompletionParameters(
        model = model,
        temperature = 0.1,
        presencePenalty = 0.0,
        maxTokens = null,
        tools = null,
      ),
  ) {

  override fun id(): String = "JIRAKIRA"

  override suspend fun onStreamChunk(chunk: ChatMessageChunk) {
    throw AppError.internal("Jira Kira does not support streaming")
  }

  override suspend fun onMessage(message: ChatMessage) {}

  override suspend fun onToolError(toolCallId: String?, e: Throwable): ToolCallResult {
    if (e is JiraError) {
      LOG.error("Jira API error:", e)
      return ToolCallResult(id = toolCallId, content = "error: ${e.message}")
    } else {
      LOG.error("Error in tool call", e)
      return ToolCallResult(id = toolCallId, content = "error: ${e.message}")
    }
  }

  override fun context(): String {
    return """
        |$JIRA_KIRA_CONTEXT
        |The JIRA user you are talking to is called ${jiraUser.displayName} and their email is ${jiraUser.email}.
        |The user is logged in to Jira as ${jiraUser.name} and their Jira user key is ${jiraUser.key}.
        |The time zone of the user is ${jiraUser.timeZone}. The current time is ${OffsetDateTime.now()}.
        """
      .trimMargin()
  }
}

suspend fun createWorklogEntry(state: JiraKiraState, input: String): String {
  LOG.debug("{} - creating worklog entry; input: {}", state.jiraUser.name, input)

  val input = Json.decodeFromString<CreateWorklogInput>(input)

  val issueKey = state.api.getIssueKey(input.issueId)

  val timeSlotAccount =
    state.timeSlotAttributeKey?.let {
      val acc = state.api.getDefaultBillingAccountForIssue(issueKey.issueKey) ?: return@let null
      val timeSlotAccount = TimeSlotAttribute(state.timeSlotAttributeKey, acc.key)
      LOG.debug("{} - using timeslot account: {}", state.jiraUser.name, timeSlotAccount)
      timeSlotAccount
    }

  val worklog = state.api.createWorklogEntry(input, state.jiraUser.key, timeSlotAccount)

  LOG.debug(
    "Worklog for issue ${input.issueId} created successfully for issue (time: {})",
    worklog.timeSpent,
  )

  state.emitter.emit(JiraKiraEvent.WorklogCreated(worklog), JiraKiraEvent::class)

  return "Success. Worklog entry ID: ${worklog.tempoWorklogId}."
}

suspend fun listUserOpenIssues(state: JiraKiraState, input: String): String {
  LOG.debug("{} - listing open issues; input: {}", state.jiraUser.name, input)

  val input = Json.decodeFromString<ProjectKey>(input)

  return state.api.getOpenIssuesForProject(input.project).joinToString("\n")
}

suspend fun getIssueId(state: JiraKiraState, input: String): String {
  LOG.debug("{} - getting ID for issue; input: {}", state.jiraUser.name, input)

  val input = Json.decodeFromString<IssueKey>(input)

  return state.api.getIssueId(input.issueKey)
}

suspend fun updateWorklogEntry(state: JiraKiraState, input: String): String {
  LOG.debug("{} - updating worklog entry; input: {}", state.jiraUser.name, input)

  val input =
    try {
      Json.decodeFromString<UpdateWorklogInput>(input)
    } catch (e: SerializationException) {
      LOG.error("Failed to decode tool call arguments: {}", input, e)
      if (e.message?.startsWith("Missing required field") == true) {
        // Sometimes gippitties can be self-healing
        return "${e.message}"
      }
      throw e
    }

  val worklog = state.api.updateWorklogEntry(input)

  LOG.debug(
    "Worklog for issue ${worklog.issue?.key} updated successfully (time: {})",
    worklog.timeSpent,
  )

  state.emitter.emit(JiraKiraEvent.WorklogUpdated(worklog), JiraKiraEvent::class)

  return "success"
}

suspend fun getIssueWorklog(state: JiraKiraState, input: String): String {
  LOG.debug("{} - getting issue worklog; input: {}", state.jiraUser.name, input)

  val input = Json.decodeFromString<IssueKeyOrId>(input)

  val worklog = state.api.getIssueWorklog(input.issueKeyOrId, state.jiraUser.key)

  val result = worklog.joinToString("\n")

  LOG.debug("{} - worklog: {}", state.jiraUser.name, result)

  return result
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
