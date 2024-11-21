package net.barrage.llmao

import com.aallam.openai.api.core.FinishReason
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.api.ws.ServerMessage
import net.barrage.llmao.app.api.ws.SystemMessage
import net.barrage.llmao.core.types.KUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

val json = Json { ignoreUnknownKeys = true }

suspend fun HttpClient.chatSession(
  sessionId: KUUID,
  block: suspend ClientWebSocketSession.() -> Unit,
) {
  val token = get("/ws") { header(HttpHeaders.Cookie, sessionCookie(sessionId)) }.bodyAsText()
  webSocket("/?token=$token") { block() }
}

suspend fun ClientWebSocketSession.sendClientSystem(message: SystemMessage) {
  val jsonMessage = Json.encodeToString(message)
  val msg = "{ \"type\": \"system\", \"payload\": $jsonMessage }"
  send(Frame.Text(msg))
}

/** Send the `chat_open_new` system message and wait for the chat_open response. */
suspend fun ClientWebSocketSession.openNewChat(agentId: KUUID) {
  // Open a chat and confirm it's open
  sendClientSystem(SystemMessage.OpenNewChat(agentId))
  val chatOpen = (incoming.receive() as Frame.Text).readText()
  val chatOpenMessage = json.decodeFromString<ServerMessage.ChatOpen>(chatOpen)
  assertNotNull(chatOpenMessage.chatId)
}

/** Send a chat message and wait for the response. */
suspend fun ClientWebSocketSession.sendMessage(text: String): String {
  val message = "{ \"type\": \"chat\", \"text\": \"$text\" }"
  send(Frame.Text(message))

  var buffer = ""

  for (frame in incoming) {
    val response = (frame as Frame.Text).readText()
    try {
      when (val finishEvent = json.decodeFromString<ServerMessage>(response)) {
        is ServerMessage.FinishEvent -> {
          assert(finishEvent.reason == FinishReason.Stop)
          assertNull(finishEvent.content)
          break
        }

        is ServerMessage.ChatTitle -> {
          assertEquals(COMPLETIONS_TITLE_RESPONSE, finishEvent.title.isNotBlank())
        }

        else -> {}
      }
    } catch (e: SerializationException) {
      val errMessage = e.message ?: throw e
      if (!errMessage.startsWith("Expected JsonObject, but had JsonLiteral")) {
        throw e
      }
      buffer += response
    } catch (e: Throwable) {
      e.printStackTrace()
      break
    }
  }

  return buffer
}

inline fun <reified T> receiveJson(message: String): T = json.decodeFromString(message)
