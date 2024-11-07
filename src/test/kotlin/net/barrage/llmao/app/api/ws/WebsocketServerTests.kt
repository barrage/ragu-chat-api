package net.barrage.llmao.app.api.ws

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WebsocketServerTests : IntegrationTest() {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var user: User
  private lateinit var session: Session

  @BeforeAll
  fun setup() {
    agent =
      postgres!!.testAgent(
        embeddingProvider = "fembed",
        embeddingModel = "Xenova/bge-large-en-v1.5",
      )
    agentConfiguration = postgres!!.testAgentConfiguration(agentId = agent.id)
    user = postgres!!.testUser(email = "not@important.org", admin = false)
    session = postgres!!.testSession(user.id)
  }

  @Test
  fun rejectsRequestNoSession() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    client.webSocket("/") {
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      val err = closeReason.await()!!.message
      assertEquals("Unauthorized", err)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun rejectsRequestMalformedToken() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    client.webSocket("/?token=foo") {
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      val err = closeReason.await()!!.message
      assertEquals("Unauthorized", err)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun rejectsRequestInvalidToken() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = KUUID.randomUUID()
    client.webSocket("/?token=$token") {
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      val err = closeReason.await()!!.message
      assertEquals("Unauthorized", err)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun rejectsMessageInvalidJson() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = getWsToken(client, sessionCookie(session.sessionId))
    client.webSocket("/?token=$token") {
      send("asdf")
      val response = (incoming.receive() as Frame.Text).readText()
      val error = receiveJson<AppError>(response)
      assertEquals("API", error.type)
      assertEquals(ErrorReason.InvalidParameter, error.reason)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingNewChatWorks() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = getWsToken(client, sessionCookie(session.sessionId))
    client.webSocket("/?token=$token") {
      val openChat = SystemMessage.OpenNewChat(agent.id)
      sendClientSystem(openChat)
      val response = (incoming.receive() as Frame.Text).readText()
      val message = receiveJson<ServerMessage.ChatOpen>(response)
      assertNotNull(message.chatId)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingNewChatWorksWithAlreadyOpenChat() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = getWsToken(client, sessionCookie(session.sessionId))
    client.webSocket("/?token=$token") {
      val openChat = SystemMessage.OpenNewChat(agent.id)

      sendClientSystem(openChat)
      val first = (incoming.receive() as Frame.Text).readText()
      val firstMessage = receiveJson<ServerMessage.ChatOpen>(first)
      assertNotNull(firstMessage.chatId)

      sendClientSystem(openChat)
      val second = (incoming.receive() as Frame.Text).readText()
      val secondMessage = receiveJson<ServerMessage.ChatOpen>(second)
      assertNotNull(secondMessage.chatId)

      assertNotEquals(firstMessage.chatId, secondMessage.chatId)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingExistingChatWorks() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = getWsToken(client, sessionCookie(session.sessionId))
    client.webSocket("/?token=$token") {
      val openChat = SystemMessage.OpenNewChat(agent.id)
      sendClientSystem(openChat)

      val first = (incoming.receive() as Frame.Text).readText()
      val firstMessage = receiveJson<ServerMessage.ChatOpen>(first)
      assertNotNull(firstMessage.chatId)

      val openExistingChat = SystemMessage.OpenExistingChat(firstMessage.chatId)
      sendClientSystem(openExistingChat)

      val second = (incoming.receive() as Frame.Text).readText()
      val secondMessage = receiveJson<ServerMessage.ChatOpen>(second)
      assertNotNull(secondMessage.chatId)

      assertEquals(firstMessage.chatId, secondMessage.chatId)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingExistingChatFailsDoesNotExist() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = getWsToken(client, sessionCookie(session.sessionId))
    client.webSocket("/?token=$token") {
      val openChat = SystemMessage.OpenExistingChat(KUUID.randomUUID())
      sendClientSystem(openChat)

      val message = (incoming.receive() as Frame.Text).readText()
      val error = receiveJson<AppError>(message)
      assertEquals("API", error.type)
      assertEquals(ErrorReason.EntityDoesNotExist, error.reason)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun closesChatOnCloseFrame() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = getWsToken(client, sessionCookie(session.sessionId))
    client.webSocket("/?token=$token") {
      send(Frame.Close())
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingChatFailsAgentDoesNotExist() = test {
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }

    var asserted = false

    val token = getWsToken(client, sessionCookie(session.sessionId))
    client.webSocket("/?token=$token") {
      val openChat = SystemMessage.OpenNewChat(KUUID.randomUUID())
      sendClientSystem(openChat)

      val message = (incoming.receive() as Frame.Text).readText()
      val error = receiveJson<AppError>(message)
      assertEquals("API", error.type)
      assertEquals(ErrorReason.EntityDoesNotExist, error.reason)
      asserted = true
    }

    assert(asserted)
  }
}

private suspend fun getWsToken(client: HttpClient, cookie: String): String {
  val res = client.get("/ws") { header(HttpHeaders.Cookie, cookie) }
  return res.bodyAsText()
}

private suspend fun ClientWebSocketSession.sendClientSystem(message: SystemMessage) {
  val jsonMessage = Json.encodeToString(message)
  val msg = "{ \"type\": \"system\", \"payload\": $jsonMessage }"
  send(Frame.Text(msg))
}

private inline fun <reified T> receiveJson(message: String): T {
  val json = Json { ignoreUnknownKeys = true }
  return json.decodeFromString(message)
}
