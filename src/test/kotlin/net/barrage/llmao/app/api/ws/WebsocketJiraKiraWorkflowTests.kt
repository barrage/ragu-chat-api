package net.barrage.llmao.app.api.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.workflow.jirakira.JiraKiraMessage
import net.barrage.llmao.chatSession
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.json
import net.barrage.llmao.openNewChat
import net.barrage.llmao.sendMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WebsocketJiraKiraWorkflowTests : IntegrationTest(useWiremock = true) {
  private lateinit var user: User
  private lateinit var session: Session

  @BeforeAll
  fun setup() {
    runBlocking {
      user = postgres.testUser(email = "not@important.org", admin = false)
      session = postgres.testSession(user.id)
      postgres.testJiraApiKey(user.id, "JIRA_API_KEY")
      postgres.testSettings(
        SettingsUpdate(listOf(SettingUpdate(SettingKey.JIRA_TIME_SLOT_ATTRIBUTE_KEY, "_Customer_")))
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

    client.chatSession(session.sessionId) {
      openNewChat(workflowType = "JIRAKIRA")

      sendMessage(
        "JIRAKIRA, create worklog for LLMAO-252, monday, added azure embedder with ada-002 support, 30 minutes starting at 7am"
      ) { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()

          try {
            val message = json.decodeFromString<JiraKiraMessage.LlmResponse>(response)
            assertEquals(COMPLETIONS_RESPONSE, message.content)
            asserted = true
            break
          } catch (_: SerializationException) {}
        }
      }
    }

    assert(asserted)
  }

  @Test
  fun jiraKiraSuccessfulCompletionWithToolsUsed() = wsTest { client ->
    var assertions = 0

    client.chatSession(session.sessionId) {
      openNewChat(workflowType = "JIRAKIRA")

      sendMessage("JIRA_KIRA_CREATE_WORKLOG") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()

          try {
            val message = json.decodeFromString<ToolEvent.ToolResult>(response)
            assertEquals("create_new_worklog_entry: success", message.result.content)
            assertions += 1
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<JiraKiraMessage.WorklogCreated>(response)
            // Has to match the response from wiremock at rest_api_2_issue_response.json
            assertEquals("RAGU-420", message.worklog.issue?.key)
            assertions += 1
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<JiraKiraMessage.LlmResponse>(response)
            assertEquals(COMPLETIONS_RESPONSE, message.content)
            assertions += 1
            break
          } catch (_: SerializationException) {}
        }
      }
    }

    assertEquals(3, assertions)
  }
}
