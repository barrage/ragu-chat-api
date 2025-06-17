package net.barrage.llmao.test

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.barrage.llmao.core.workflow.DefaultWorkflowInput
import net.barrage.llmao.core.workflow.IncomingSystemMessage

val json = Json { ignoreUnknownKeys = true }

suspend fun HttpClient.adminWsSession(block: suspend ClientWebSocketSession.() -> Unit) {
  val token = get("/ws") { header(HttpHeaders.Cookie, adminAccessToken()) }.bodyAsText()
  webSocket("/?token=$token") { block() }
}

suspend fun HttpClient.userWsSession(block: suspend ClientWebSocketSession.() -> Unit) {
  val token = get("/ws") { header(HttpHeaders.Cookie, userAccessToken()) }.bodyAsText()
  webSocket("/?token=$token") { block() }
}

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

inline fun <reified T> receiveJson(message: String): T = json.decodeFromString(message)
