import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.SerializationException
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.core.workflow.StreamChunk
import net.barrage.llmao.core.workflow.StreamComplete
import net.barrage.llmao.tables.references.APPLICATION_SETTINGS
import net.barrage.llmao.tables.references.JIRA_API_KEYS
import net.barrage.llmao.tables.references.JIRA_WORKLOG_ATTRIBUTES
import org.jooq.impl.DSL.excluded
import org.junit.jupiter.api.Assertions.assertNotNull

/** Send the `chat_open_new` system message and wait for the chat_open response. */
suspend fun ClientWebSocketSession.openNewChat(
  agentId: KUUID? = null,
  workflowType: String = "CHAT",
): KUUID {
  // Open a chat and confirm it's open
  sendClientSystem(IncomingSystemMessage.CreateNewWorkflow(workflowType, null))
  val chatOpen = (incoming.receive() as Frame.Text).readText()
  val workflowOpenMessage = json.decodeFromString<OutgoingSystemMessage.WorkflowOpen>(chatOpen)
  assertNotNull(workflowOpenMessage.id)
  return workflowOpenMessage.id
}

suspend fun ClientWebSocketSession.openExistingChat(
  chatId: KUUID,
  workflowType: String = "CHAT",
): KUUID {
  // Open a chat and confirm it's open
  sendClientSystem(IncomingSystemMessage.LoadExistingWorkflow(workflowType, chatId))
  val chatOpen = (incoming.receive() as Frame.Text).readText()
  val workflowOpenMessage = json.decodeFromString<OutgoingSystemMessage.WorkflowOpen>(chatOpen)
  assertNotNull(workflowOpenMessage.id)
  return workflowOpenMessage.id
}

/** Open a new chat, send a message and collect the response. */
suspend fun HttpClient.openSendAndCollect(
  agentId: KUUID? = null,
  chatId: KUUID? = null,
  message: String,
): Pair<KUUID, String> {
  var buffer = ""
  lateinit var openChatId: KUUID

  adminWsSession {
    openChatId =
      agentId?.let { openNewChat(it) }
        ?: chatId?.let { openExistingChat(it) }
        ?: throw IllegalArgumentException("Must provide either agentId or chatId")

    sendMessage(message) { incoming ->
      for (frame in incoming) {
        val response = (frame as Frame.Text).readText()
        try {
          val message = json.decodeFromString<StreamChunk>(response)
          buffer += message.chunk
        } catch (_: SerializationException) {}

        try {
          val message = json.decodeFromString<StreamComplete>(response)
          assert(message.reason == FinishReason.Stop)
          break
        } catch (_: SerializationException) {}

        try {
          val message = json.decodeFromString<AppError>(response)
          throw message
          break
        } catch (_: SerializationException) {}
      }
    }
  }

  return Pair(openChatId, buffer)
}

suspend fun TestPostgres.testJiraApiKey(userId: String, apiKey: String) {
  dslContext
    .insertInto(JIRA_API_KEYS)
    .set(JIRA_API_KEYS.USER_ID, userId)
    .set(JIRA_API_KEYS.API_KEY, apiKey)
    .awaitSingle()
}

suspend fun TestPostgres.testJiraWorklogAttribute(
  id: String,
  description: String,
  required: Boolean,
) {
  dslContext
    .insertInto(JIRA_WORKLOG_ATTRIBUTES)
    .set(JIRA_WORKLOG_ATTRIBUTES.ID, id)
    .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
    .set(JIRA_WORKLOG_ATTRIBUTES.REQUIRED, required)
    .awaitSingle()
}

suspend fun TestPostgres.testSettings(settings: SettingsUpdate) {
  settings.removals?.forEach { key ->
    dslContext
      .deleteFrom(APPLICATION_SETTINGS)
      .where(APPLICATION_SETTINGS.NAME.eq(key))
      .awaitSingle()
  }

  settings.updates?.let { updates ->
    dslContext
      .insertInto(APPLICATION_SETTINGS, APPLICATION_SETTINGS.NAME, APPLICATION_SETTINGS.VALUE)
      .apply { updates.forEach { setting -> values(setting.key, setting.value) } }
      .onConflict(APPLICATION_SETTINGS.NAME)
      .doUpdate()
      .set(APPLICATION_SETTINGS.VALUE, excluded(APPLICATION_SETTINGS.VALUE))
      .awaitSingle()
  }
}
