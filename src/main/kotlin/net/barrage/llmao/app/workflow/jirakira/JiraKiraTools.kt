package net.barrage.llmao.app.workflow.jirakira

import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolFunctionDefinition
import net.barrage.llmao.core.llm.ToolFunctionParameters
import net.barrage.llmao.core.llm.ToolPropertyDefinition

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
