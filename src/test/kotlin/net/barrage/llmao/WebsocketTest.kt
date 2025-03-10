package net.barrage.llmao

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import org.junit.jupiter.api.Assertions.assertNotNull

val json = Json { ignoreUnknownKeys = true }

suspend fun HttpClient.wsSession(
  block: suspend ClientWebSocketSession.() -> Unit,
) {
  val token = get("/ws") { header(HttpHeaders.Cookie, adminAccessToken()) }.bodyAsText()
  webSocket("/?token=$token") { block() }
}

suspend fun ClientWebSocketSession.sendClientSystem(message: IncomingSystemMessage) {
  val jsonMessage = Json.encodeToString(message)
  val msg = "{ \"type\": \"system\", \"payload\": $jsonMessage }"
  send(Frame.Text(msg))
}

/** Send the `chat_open_new` system message and wait for the chat_open response. */
suspend fun ClientWebSocketSession.openNewChat(
  agentId: KUUID? = null,
  workflowType: String? = null,
): KUUID {
  // Open a chat and confirm it's open
  sendClientSystem(IncomingSystemMessage.CreateNewWorkflow(agentId, workflowType))
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
  val message = "{ \"type\": \"chat\", \"text\": \"$text\" }"
  send(Frame.Text(message))
  block(incoming)
}

inline fun <reified T> receiveJson(message: String): T = json.decodeFromString(message)
