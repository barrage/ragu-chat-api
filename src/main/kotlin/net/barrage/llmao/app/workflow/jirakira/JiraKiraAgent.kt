package net.barrage.llmao.app.workflow.jirakira

import io.ktor.util.logging.KtorSimpleLogger
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.WorkflowAgent

internal val LOG = KtorSimpleLogger("n.b.l.a.workflow.jirakira.JiraKiraAgent")

class JiraKira(
  private val jiraUser: JiraUser,
  user: User,
  tokenTracker: TokenUsageTracker,
  history: ChatHistory,
  inferenceProvider: InferenceProvider,
  model: String,
) :
  WorkflowAgent(
    id = JIRAKIRA_WORKFLOW_ID,
    user = user,
    model = model,
    inferenceProvider = inferenceProvider,
    tokenTracker = tokenTracker,
    contextEnrichment = null,
    history = history,
    completionParameters =
      ChatCompletionBaseParameters(
        model = model,
        temperature = 0.1,
        presencePenalty = 0.0,
        maxTokens = null,
      ),
  ) {

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

class JiraKiraToolExecutor(
  /** Output handle for Jira related events, such as worklog creation. */
  val emitter: Emitter,

  /** The Jira user obtained from the API that initialized the workflow. */
  val jiraUser: JiraUser,

  /** The Jira API client. */
  val api: JiraApi,

  /**
   * The key of the time slot attribute to use when creating worklog entries. The key for this
   * attribute is set in Jira and must be configured into the app via the application settings.
   *
   * If not present, the time slot will not be present on created worklogs. The value for this key
   * is obtained per issue.
   */
  val timeSlotAttributeKey: String?,
) {
  suspend fun createWorklogEntry(input: String): String {
    LOG.debug("{} - creating worklog entry; input: {}", jiraUser.name, input)

    val input = Json.decodeFromString<CreateWorklogInput>(input)

    val issueKey = api.getIssueKey(input.issueId)

    val timeSlotAccount =
      timeSlotAttributeKey?.let {
        val acc = api.getDefaultBillingAccountForIssue(issueKey.issueKey) ?: return@let null
        val timeSlotAccount = TimeSlotAttribute(timeSlotAttributeKey, acc.key)
        LOG.debug("{} - using timeslot account: {}", jiraUser.name, timeSlotAccount)
        timeSlotAccount
      }

    val worklog = api.createWorklogEntry(input, jiraUser.key, timeSlotAccount)

    LOG.debug(
      "Worklog for issue ${input.issueId} created successfully for issue (time: {})",
      worklog.timeSpent,
    )

    emitter.emit(
      JiraKiraWorkflowOutput.WorklogCreated(worklog),
      JiraKiraWorkflowOutput.serializer(),
    )

    return "Success. Worklog entry ID: ${worklog.tempoWorklogId}."
  }

  suspend fun listUserOpenIssues(input: String): String {
    LOG.debug("{} - listing open issues; input: {}", jiraUser.name, input)

    val input = Json.decodeFromString<ProjectKey>(input)

    return api.getOpenIssuesForProject(input.project).joinToString("\n")
  }

  suspend fun getIssueId(input: String): String {
    LOG.debug("{} - getting ID for issue; input: {}", jiraUser.name, input)

    val input = Json.decodeFromString<IssueKey>(input)

    return api.getIssueId(input.issueKey)
  }

  suspend fun updateWorklogEntry(input: String): String {
    LOG.debug("{} - updating worklog entry; input: {}", jiraUser.name, input)

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

    val worklog = api.updateWorklogEntry(input)

    LOG.debug(
      "Worklog for issue ${worklog.issue?.key} updated successfully (time: {})",
      worklog.timeSpent,
    )

    emitter.emit(
      JiraKiraWorkflowOutput.WorklogUpdated(worklog),
      JiraKiraWorkflowOutput.serializer(),
    )

    return "success"
  }

  suspend fun getIssueWorklog(input: String): String {
    LOG.debug("{} - getting issue worklog; input: {}", jiraUser.name, input)

    val input = Json.decodeFromString<IssueKeyOrId>(input)

    val worklog = api.getIssueWorklog(input.issueKeyOrId, jiraUser.key)

    val result = worklog.joinToString("\n")

    LOG.debug("{} - worklog: {}", jiraUser.name, result)

    return result
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
