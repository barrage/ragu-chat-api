package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.util.*
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.api.http.dto.EvaluateMessageDTO
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminChatControllerTests : IntegrationTest() {
  private lateinit var user: User
  private lateinit var userAdmin: User
  private lateinit var userSession: Session
  private lateinit var userAdminSession: Session
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var chatOne: Chat
  private lateinit var chatTwo: Chat
  private lateinit var chatThree: Chat
  private lateinit var messageOne: Message
  private lateinit var messageTwo: Message
  private lateinit var chatRepository: ChatRepository

  @BeforeAll
  fun setup() {
    user = postgres.testUser(email = "not@important.org", admin = false)
    userAdmin = postgres.testUser(admin = true)
    userSession = postgres.testSession(user.id)
    userAdminSession = postgres.testSession(userAdmin.id)
    agent = postgres.testAgent(active = true)
    agentConfiguration = postgres.testAgentConfiguration(agent.id)
    chatOne = postgres.testChat(user.id, agent.id)
    chatTwo = postgres.testChat(user.id, agent.id)
    chatThree = postgres.testChat(userAdmin.id, agent.id)
    messageOne = postgres.testChatMessage(chatOne.id, user.id, "First Message")
    messageTwo = postgres.testChatMessage(chatOne.id, user.id, "Second Message")
    chatRepository = ChatRepository(postgres.dslContext)
  }

  @Test
  fun shouldRetrieveAllChatsDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithUserAndAgent>>()
    assertNotNull(body)
    assertEquals(3, body.total)
  }

  @Test
  fun shouldRetrieveAllChatsFilterByUserId() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        parameter("userId", user.id)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithUserAndAgent>>()
    assertNotNull(body)
    assertEquals(2, body.total)
  }

  @Test
  fun shouldFailGetAllChatsForNonAdminUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
    val body = response.body<String>()
    assertNotNull(body)
  }

  @Test
  fun shouldUpdateChatTitle() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val newTitle = "Updated Chat Title"
    val updatedChatTitle = UpdateChatTitleDTO(newTitle)
    val response =
      client.put("/admin/chats/${chatOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updatedChatTitle)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<String>()
    assertNotNull(body)
    assertEquals("Chat title successfully updated", body)
  }

  @Test
  fun shouldRetrieveMessagesForChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats/${chatOne.id}/messages") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<Message>>()
    assertNotNull(body)
    assertEquals(2, body.size)
    // Messages are sorted by createdAt DESC
    assertEquals(messageOne.id, body[1].id)
    assertEquals(messageTwo.id, body[0].id)
  }

  @Test
  fun shouldThrowErrorWhenRetrievingMessagesForNonExistingChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats/${UUID.randomUUID()}/messages") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun shouldEvaluateMessageFromChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessageDTO(true)
    val response =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<Message>()
    assertNotNull(body)
    assertEquals(messageOne.id, body.id)
    assertEquals(true, body.evaluation)
  }

  @Test
  fun shouldGetChatWithUserAndAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats/${chatOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<ChatWithUserAndAgent>()
    assertEquals(chatOne.id, body.chat.id)
    assertEquals(user.id, body.user.id)
    assertEquals(agent.id, body.agent.id)
  }

  @Test
  fun shouldDeleteChatWithExistingMessages() = test {
    val newChat = postgres.testChat(user.id, agent.id)
    val chat = chatRepository.get(newChat.id)!!
    val userMessage = chatRepository.insertUserMessage(chat.id, user.id, "Test Message")
    val agentMessage =
      chatRepository.insertAssistantMessage(chat.id, agent.id, "Test Message", userMessage.id)
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/admin/chats/${chat.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.NoContent, response.status)

    val deletedChat = chatRepository.get(chat.id)

    assertNull(deletedChat)

    val deletedUserMessage = chatRepository.getMessage(chat.id, userMessage.id)
    val deletedAgentMessage = chatRepository.getMessage(chat.id, agentMessage.id)

    assertNull(deletedUserMessage)
    assertNull(deletedAgentMessage)
  }
}
