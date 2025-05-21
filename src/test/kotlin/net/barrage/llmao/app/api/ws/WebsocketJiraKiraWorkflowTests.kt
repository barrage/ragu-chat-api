package net.barrage.llmao.app.api.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import net.barrage.llmao.ADMIN_USER
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.adminWsSession
import net.barrage.llmao.app.workflow.jirakira.JiraKiraLlmProvider
import net.barrage.llmao.app.workflow.jirakira.JiraKiraModel
import net.barrage.llmao.app.workflow.jirakira.JiraKiraTimeSlotAttributeKey
import net.barrage.llmao.app.workflow.jirakira.JiraKiraWorkflowOutput
import net.barrage.llmao.core.administration.settings.SettingUpdate
import net.barrage.llmao.core.administration.settings.SettingsUpdate
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.workflow.StreamComplete
import net.barrage.llmao.json
import net.barrage.llmao.openNewChat
import net.barrage.llmao.sendMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WebsocketJiraKiraWorkflowTests : IntegrationTest() {
  @BeforeAll
  fun setup() {
    runBlocking {
      postgres.testJiraApiKey(ADMIN_USER.id, "JIRA_API_KEY")
      postgres.testSettings(
        SettingsUpdate(
          listOf(
            SettingUpdate(JiraKiraTimeSlotAttributeKey.KEY, "_Customer_"),
            SettingUpdate(JiraKiraLlmProvider.KEY, "openai"),
            SettingUpdate(JiraKiraModel.KEY, "gpt-4o-mini"),
          )
        )
      )
      postgres.testJiraWorklogAttribute(
        "_WorkCategory_",
        "The category of work done on the task.",
        true,
      )
    }
  }

  @Test
  fun jiraKiraSuccessfulCompletionNoToolsUsed() = wsTest { client ->
    var asserted = false

    client.adminWsSession {
      openNewChat(workflowType = "JIRAKIRA")

      sendMessage(
        "JIRAKIRA, create worklog for LLMAO-252, monday, added azure embedder with ada-002 support, 30 minutes starting at 7am"
      ) { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()

          try {
            val message = json.decodeFromString<StreamComplete>(response)
            assertEquals(COMPLETIONS_RESPONSE, message.content)
            asserted = true
            break
          } catch (_: SerializationException) {}
        }
      }
    }

    assert(asserted)
    // We have to wait here because pool can get shut down
    delay(2000)
  }

  @Test
  fun jiraKiraSuccessfulCompletionWithToolsUsed() = wsTest { client ->
    var assertions = 0

    client.adminWsSession {
      openNewChat(workflowType = "JIRAKIRA")

      sendMessage("JIRA_KIRA_CREATE_WORKLOG") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()

          try {
            val message = json.decodeFromString<ToolEvent.ToolResult>(response)
            assertEquals("Success. Worklog entry ID: 464710.", message.result)
            assertions += 1
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<JiraKiraWorkflowOutput.WorklogCreated>(response)
            // Has to match the response from wiremock at rest_api_2_issue_response.json
            assertEquals("RAGU-420", message.worklog.issue?.key)
            assertions += 1
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<StreamComplete>(response)
            assertEquals(COMPLETIONS_RESPONSE, message.content)
            assertions += 1
            break
          } catch (_: SerializationException) {}
        }
      }
    }

    assertEquals(3, assertions)
    // We have to wait here because pool can get shut down
    delay(2000)
  }
}
