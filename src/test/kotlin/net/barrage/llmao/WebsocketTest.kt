package net.barrage.llmao

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.barrage.llmao.app.workflow.chat.NewChatWorkflow
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.workflow.ChatWorkflowInput
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.types.KUUID
import org.junit.jupiter.api.Assertions.assertNotNull

val json = Json { ignoreUnknownKeys = true }

suspend fun HttpClient.adminWsSession(block: suspend ClientWebSocketSession.() -> Unit) {
  val token = get("/ws") { header(HttpHeaders.Cookie, adminAccessToken()) }.bodyAsText()
  webSocket("/?token=$token") { block() }
}

suspend fun HttpClient.userWsSession(block: suspend ClientWebSocketSession.() -> Unit) {
  val token = get("/ws") { header(HttpHeaders.Cookie, userAccessToken()) }.bodyAsText()
  webSocket("/?token=$token") { block() }
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
          val message = json.decodeFromString<WorkflowOutput.StreamChunk>(response)
          buffer += message.chunk
        } catch (_: SerializationException) {}

        try {
          val message = json.decodeFromString<WorkflowOutput.StreamComplete>(response)
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

suspend fun ClientWebSocketSession.sendClientSystem(message: IncomingSystemMessage) {
  send(Frame.Text(Json.encodeToString(message)))
}

/** Send the `chat_open_new` system message and wait for the chat_open response. */
suspend fun ClientWebSocketSession.openNewChat(
  agentId: KUUID? = null,
  workflowType: String = "CHAT",
): KUUID {
  // Open a chat and confirm it's open
  sendClientSystem(
    IncomingSystemMessage.CreateNewWorkflow(
      workflowType,
      agentId?.let { Json.encodeToJsonElement(NewChatWorkflow(it)) },
    )
  )
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

/** Send a chat message and wait for the response. */
suspend fun ClientWebSocketSession.sendMessage(
  text: String,
  block: suspend (ReceiveChannel<Frame>) -> Unit,
) {
  send(Frame.Text(json.encodeToString(ChatWorkflowInput(text))))
  block(incoming)
}

inline fun <reified T> receiveJson(message: String): T = json.decodeFromString(message)
