package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.api.http.dto.EvaluateMessageDTO
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatControllerTests : IntegrationTest() {
  private lateinit var user: User
  private lateinit var userSession: Session
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var chatOne: Chat
  private lateinit var chatTwo: Chat
  private lateinit var messageOne: Message
  private lateinit var messageTwo: Message

  @BeforeAll
  fun setup() {
    user = postgres!!.testUser(admin = false)
    userSession = postgres!!.testSession(user.id)
    agent = postgres!!.testAgent(active = true)
    agentConfiguration = postgres!!.testAgentConfiguration(agent.id)
    chatOne = postgres!!.testChat(user.id, agent.id)
    chatTwo = postgres!!.testChat(user.id, agent.id)
    messageOne = postgres!!.testChatMessage(chatOne.id, user.id, "First Message")
    messageTwo = postgres!!.testChatMessage(chatOne.id, user.id, "Second Message")
  }

  @Test
  fun shouldRetrieveAllChatsDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.get("/chats") { header("Cookie", sessionCookie(userSession.sessionId)) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Chat>>()
    assertEquals(2, body.total)
    assertEquals(chatOne.id, body.items[0].id)
    assertEquals(chatTwo.id, body.items[1].id)
  }

  @Test
  fun shouldRetrieveAllChatsSortedByCreatedAt() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/chats?sortBy=createdAt&sortOrder=desc") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Chat>>()
    assertEquals(2, body.total)
    assertEquals(chatOne.id, body.items[1].id)
    assertEquals(chatTwo.id, body.items[0].id)
  }

  @Test
  fun shouldUpdateChatTitle() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updatedTitle = UpdateChatTitleDTO("Updated Title")
    val response =
      client.put("/chats/${chatOne.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updatedTitle)
      }

    assertEquals(200, response.status.value)
    val body = response.body<String>()
    assertEquals("", body)
  }

  @Test
  fun shouldDeleteChat() = test {
    val chatDelete = postgres!!.testChat(user.id, agent.id)
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/chats/${chatDelete.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(204, response.status.value)
    val body = response.body<String>()
    assertEquals("", body)

    val check =
      client.get("/chats/${chatDelete.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(404, check.status.value)
  }

  @Test
  fun shouldRetrieveChatMessages() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/chats/${chatOne.id}/messages") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<List<Message>>()
    assertEquals(2, body.size)
    assertEquals(messageOne.id, body[0].id)
    assertEquals(messageTwo.id, body[1].id)
  }

  @Test
  fun shouldEvaluateMessage() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessageDTO(true)
    val response =
      client.patch("/chats/${chatOne.id}/messages/${messageOne.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }

    assertEquals(200, response.status.value)
    val body = response.body<Message>()
    assertEquals(messageOne.id, body.id)
    assertEquals(true, body.evaluation)
  }

  @Test
  fun shouldRetrieveChatWithAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/chats/${chatOne.id}") { header("Cookie", sessionCookie(userSession.sessionId)) }

    assertEquals(200, response.status.value)
    val body = response.body<ChatWithAgent>()
    assertEquals(chatOne.id, body.chat.id)
    assertEquals(agent.id, body.agent.id)
  }
}
