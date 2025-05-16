package net.barrage.llmao.app.workflow.jirakira

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolFunctionDefinition
import net.barrage.llmao.core.llm.ToolFunctionParameters
import net.barrage.llmao.core.llm.ToolPropertyDefinition
import net.barrage.llmao.core.llm.Tools
import net.barrage.llmao.core.llm.ToolsBuilder
import net.barrage.llmao.core.workflow.Emitter

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
  fun tools(
    attributes: List<TempoWorkAttribute>,

    /**
     * A set of attributes that are allowed/required to be used in worklog entries. This is
     * predefined in jira and will be obtained via the Jira API when instantiating JiraKira. These
     * attributes will be included in the JSON schema for the [CreateWorklogEntrySchema] tool.
     */
    worklogAttributes: List<WorklogAttribute>,
  ): Tools {
    val tools = loadTools(attributes, worklogAttributes)
    val builder = ToolsBuilder()
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
    return builder.build()
  }

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

  /**
   * Load tool JSON schema definitions based on custom worklog attributes.
   *
   * This method will load all custom worklog attributes from the Jira API and include them in the
   * tool definitions.
   *
   * All attributes are obtained from the Jira API and are matched against the ones defined in the
   * database. Only those that are present in the database will be included in the tool definitions.
   */
  private fun loadTools(
    attributes: List<TempoWorkAttribute>,
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

/** Input for the [TOOL_GET_ISSUE_ID] tool. */
@Serializable data class IssueKey(val issueKey: String)

@Serializable data class IssueKeyOrId(val issueKeyOrId: String)

@Serializable data class ProjectKey(val project: String)

const val TOOL_LIST_USER_OPEN_ISSUES = "list_user_open_issues"
const val TOOL_GET_ISSUE_ID = "get_issue_id"
const val TOOL_GET_ISSUE_WORKLOG = "get_issue_worklog"
const val TOOL_CREATE_WORKLOG_ENTRY = "create_new_worklog_entry"
const val TOOL_UPDATE_WORKLOG_ENTRY = "update_existing_worklog_entry"

val GetIssueIdSchema =
  ToolDefinition(
    type = "function",
    function =
      ToolFunctionDefinition(
        name = TOOL_GET_ISSUE_ID,
        description =
          "Get the ID of a Jira issue (task) based on its key. When creating a worklog entry, you must use the ID.",
        parameters =
          ToolFunctionParameters(
            type = "object",
            properties =
              mapOf(
                "issueKey" to
                  ToolPropertyDefinition(
                    type = "string",
                    description = "The key of the issue to get the ID for.",
                  )
              ),
            required = listOf("issueKey"),
          ),
        strict = true,
      ),
  )

val CreateWorklogEntrySchema =
  ToolDefinition(
    type = "function",
    function =
      ToolFunctionDefinition(
        name = TOOL_CREATE_WORKLOG_ENTRY,
        description = "Create a worklog entry for a Jira issue (task).",
        parameters =
          ToolFunctionParameters(
            type = "object",
            properties =
              mapOf(
                "issueId" to
                  ToolPropertyDefinition(
                    type = "string",
                    description =
                      "The ID of the issue to create the worklog for. Absolutely necessary.",
                  ),
                "comment" to
                  ToolPropertyDefinition(
                    type = "string",
                    description =
                      """The comment to add to the worklog entry. Make it sound professional.
                        | Describes what was done when working on the task.
                        | You will determine this based on the user input.
                        | You will correct any spelling and grammar errors in the user input."""
                        .trimMargin(),
                  ),
                "started" to
                  ToolPropertyDefinition(
                    type = "string",
                    description = "The start date time of the worklog in ISO 8601 format.",
                  ),
                "timeSpentSeconds" to
                  ToolPropertyDefinition(
                    type = "number",
                    description = "The time spent working on the task in seconds.",
                  ),
              ),
            required = listOf("issueId", "comment", "started", "timeSpentSeconds"),
          ),
        strict = true,
      ),
  )

val UpdateWorklogEntrySchema =
  ToolDefinition(
    type = "function",
    function =
      ToolFunctionDefinition(
        name = TOOL_UPDATE_WORKLOG_ENTRY,
        description =
          """Update a worklog entry for a Jira issue (task).
            | If you are unsure about any parameter, first try to list the existing worklog entries for the issue.
            | If still uncertain, ask the user for the missing information."""
            .trimMargin(),
        parameters =
          ToolFunctionParameters(
            type = "object",
            properties =
              mapOf(
                "worklogEntryId" to
                  ToolPropertyDefinition(
                    type = "number",
                    description = "The ID of the worklog entry to update. Absolutely necessary.",
                  ),
                "started" to
                  ToolPropertyDefinition(
                    type = "string",
                    description =
                      "The start date time of the worklog in ISO 8601 format. Must be present if timeSpentSeconds is present.",
                  ),
                "timeSpentSeconds" to
                  ToolPropertyDefinition(
                    type = "number",
                    description =
                      "The time spent working on the task in seconds. Must be present if started is present.",
                  ),
                "comment" to
                  ToolPropertyDefinition(
                    type = "string",
                    description =
                      """Updates the worklog entry's comment. Required.
                        | If not specified for updating, use the existing comment.
                      """
                        .trimMargin(),
                  ),
              ),
            required = listOf("worklogEntryId", "comment"),
          ),
        strict = true,
      ),
  )

val GetIssueWorklogSchema =
  ToolDefinition(
    type = "function",
    function =
      ToolFunctionDefinition(
        name = TOOL_GET_ISSUE_WORKLOG,
        description =
          "Get the worklog for a Jira issue (task). Returns a list of worklog entries and their IDs created by the user.",
        parameters =
          ToolFunctionParameters(
            type = "object",
            properties =
              mapOf(
                "issueKeyOrId" to
                  ToolPropertyDefinition(
                    type = "string",
                    description = "The key or ID of the issue to get the worklog for.",
                  )
              ),
            required = listOf("issueKeyOrId"),
          ),
        strict = true,
      ),
  )

val ListUserOpenIssuesSchema =
  ToolDefinition(
    type = "function",
    function =
      ToolFunctionDefinition(
        name = TOOL_LIST_USER_OPEN_ISSUES,
        description = "List all open issues for the current user.",
        parameters =
          ToolFunctionParameters(
            type = "object",
            properties =
              mapOf(
                "project" to
                  ToolPropertyDefinition(
                    type = "string",
                    description = "The project to list open issues for.",
                  )
              ),
            required = listOf("project"),
          ),
        strict = true,
      ),
  )
