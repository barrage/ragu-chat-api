package net.barrage.llmao.test

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.DefaultWorkflowInput
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import org.junit.jupiter.api.Assertions.assertNotNull

val json = Json { ignoreUnknownKeys = true }

/** Establish a websocket connection as an admin user. */
suspend fun HttpClient.adminWsSession(block: suspend ClientWebSocketSession.() -> Unit) {
  val token = get("/ws") { header(HttpHeaders.Cookie, adminAccessToken()) }.bodyAsText()
  webSocket("/?token=$token") { block() }
}

/** Establish a websocket connection as a peasant user. */
suspend fun HttpClient.userWsSession(block: suspend ClientWebSocketSession.() -> Unit) {
  val token = get("/ws") { header(HttpHeaders.Cookie, userAccessToken()) }.bodyAsText()
  webSocket("/?token=$token") { block() }
}

/** Shorthand for sending a system message. */
suspend fun ClientWebSocketSession.sendClientSystem(message: IncomingSystemMessage) {
  send(Frame.Text(Json.encodeToString(message)))
}

/** Send a chat message and wait for the response. */
suspend fun ClientWebSocketSession.sendMessage(
  text: String,
  block: suspend (ReceiveChannel<Frame>) -> Unit,
) {
  val input = Json.encodeToJsonElement(DefaultWorkflowInput(text))
  send(
    Frame.Text(
      json.encodeToString(
        IncomingSystemMessage.serializer(),
        IncomingSystemMessage.WorkflowInput(input),
      )
    )
  )
  block(incoming)
}

/** Send the `workflow.new` system message and wait for the `workflow.open` response. */
suspend fun ClientWebSocketSession.openNewWorkflow(
  workflowType: String,
  params: JsonElement? = null,
): KUUID {
  // Open a workflow and confirm it's open
  sendClientSystem(IncomingSystemMessage.CreateNewWorkflow(workflowType, params))
  val chatOpen = (incoming.receive() as Frame.Text).readText()
  val workflowOpenMessage = json.decodeFromString<OutgoingSystemMessage.WorkflowOpen>(chatOpen)
  assertNotNull(workflowOpenMessage.id)
  return workflowOpenMessage.id
}

suspend fun ClientWebSocketSession.openExistingWorkflow(
  workflowId: KUUID,
  workflowType: String,
): KUUID {
  // Open a chat and confirm it's open
  sendClientSystem(IncomingSystemMessage.LoadExistingWorkflow(workflowType, workflowId))
  val chatOpen = (incoming.receive() as Frame.Text).readText()
  val workflowOpenMessage = json.decodeFromString<OutgoingSystemMessage.WorkflowOpen>(chatOpen)
  assertNotNull(workflowOpenMessage.id)
  return workflowOpenMessage.id
}

inline fun <reified T> decode(message: String): T = json.decodeFromString(message)
