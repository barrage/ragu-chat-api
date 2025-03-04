package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.util.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.app.workflow.chat.ChatWorkflowRepository
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.EvaluateMessage
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.MessageInsert
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import net.barrage.llmao.utils.ValidationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AdminChatWorkflowControllerTests : IntegrationTest() {
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
  private lateinit var chatWorkflowRepository: ChatWorkflowRepository
  private lateinit var chatRepository: ChatRepository

  @BeforeAll
  fun setup() {
    runBlocking {
      user = postgres.testUser(email = "not@important.org", admin = false)
      userAdmin = postgres.testUser(admin = true)
      userSession = postgres.testSession(user.id)
      userAdminSession = postgres.testSession(userAdmin.id)
      agent = postgres.testAgent(active = true)
      agentConfiguration = postgres.testAgentConfiguration(agent.id)
      chatOne = postgres.testChat(user.id, agent.id)
      chatTwo = postgres.testChat(user.id, agent.id)
      chatThree = postgres.testChat(userAdmin.id, agent.id)
      messageOne =
        postgres.testChatMessage(chatId = chatOne.id, userId = user.id, content = "First Message")
      messageTwo =
        postgres.testChatMessage(
          chatId = chatOne.id,
          userId = UUID.randomUUID(),
          content = "Second Message",
          senderType = "assistant",
          responseTo = messageOne.id,
        )
      chatWorkflowRepository = ChatWorkflowRepository(postgres.dslContext)
      chatRepository = ChatRepository(postgres.dslContext)
    }
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
    assertEquals(3, body.items.size)
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
    assertEquals(2, body.items.size)
  }

  @Test
  fun shouldRetrieveAllChatsFilterByAgentId() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        parameter("agentId", agent.id)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithUserAndAgent>>()
    assertNotNull(body)
    assertEquals(3, body.total)
    assertEquals(3, body.items.size)
  }

  @Test
  fun shouldRetrieveAllChatsFilterByTitle() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        parameter("title", "Chat")
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithUserAndAgent>>()
    assertNotNull(body)
    assertEquals(3, body.total)
    assertEquals(3, body.items.size)
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
    val body = response.body<Chat>()
    assertEquals(chatOne.id, body.id)
    assertEquals("Updated Chat Title", body.title)
  }

  @Test
  fun shouldUpdateChatTitleValidationFails() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updatedChatTitle = UpdateChatTitleDTO("")
    val response =
      client.put("/admin/chats/${chatOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updatedChatTitle)
      }
    assertEquals(422, response.status.value)
    val body = response.body<List<ValidationError>>()
    assertEquals("title", body[0].fieldName)
  }

  @Test
  fun shouldRetrieveMessagesForChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats/${chatOne.id}/messages?page=1&perPage=25") {
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
      client.get("/admin/chats/${UUID.randomUUID()}/messages?page=1&perPage=25") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun shouldEvaluateMessageFromChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessage(true)
    val response =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<EvaluateMessage>()
    assertNotNull(body)
    assertEquals(true, body.evaluation)
  }

  @Test
  fun shouldFailEvaluateMessageFromChatNotAssistantMessage() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessage(true)
    val response =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }
    assertEquals(HttpStatusCode.BadRequest, response.status)
    val body = response.body<AppError>()
    assertNotNull(body)
    assertEquals(ErrorReason.InvalidParameter, body.errorReason)
  }

  @Test
  fun shouldEvaluateMessageFromChatWithFeedback() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessage(true, "oh yes, what a splendid response")
    val response =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<EvaluateMessage>()
    assertNotNull(body)
    assertEquals(true, body.evaluation)
    assertEquals("oh yes, what a splendid response", body.feedback)
  }

  @Test
  fun shouldRemoveEvaluateMessageFromChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluationOne = EvaluateMessage(true, "what a marvelous response")
    val responseOne =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluationOne)
      }
    assertEquals(HttpStatusCode.OK, responseOne.status)

    val responseTwo =
      client.get("/admin/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, responseTwo.status)

    val bodyOne = responseTwo.body<List<Message>>()
    assertEquals(1, bodyOne.size)
    assertEquals(messageTwo.id, bodyOne[0].id)
    assertEquals(true, bodyOne[0].evaluation)
    assertEquals("what a marvelous response", bodyOne[0].feedback)

    val evaluationTwo = EvaluateMessage(null)
    val responseThree =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluationTwo)
      }
    assertEquals(HttpStatusCode.OK, responseThree.status)
    val bodyTwo = responseThree.body<EvaluateMessage>()
    assertEquals(null, bodyTwo.evaluation)
    assertEquals(null, bodyTwo.feedback)

    val responseFour =
      client.get("/admin/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, responseFour.status)

    val bodyThree = responseFour.body<List<Message>>()
    assertEquals(1, bodyThree.size)
    assertEquals(messageTwo.id, bodyThree[0].id)
    assertEquals(null, bodyThree[0].evaluation)
    assertEquals(null, bodyThree[0].feedback)
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
    val chat = chatWorkflowRepository.getChatWithMessages(newChat.id)

    val userMessage =
      MessageInsert(
        id = UUID.randomUUID(),
        chatId = chat.chat.id,
        content = "Test Message",
        sender = user.id,
        senderType = "user",
        responseTo = null,
        finishReason = null,
      )

    val agentMessage =
      MessageInsert(
        id = UUID.randomUUID(),
        chatId = chat.chat.id,
        content = "Test Message",
        sender = agent.id,
        senderType = "assistant",
        responseTo = userMessage.id,
        finishReason = FinishReason.Stop,
      )

    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/admin/chats/${chat.chat.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.NoContent, response.status)

    assertThrows<AppError> { chatWorkflowRepository.getChatWithMessages(chat.chat.id) }

    val deletedUserMessage = chatRepository.getMessage(chat.chat.id, userMessage.id)
    val deletedAgentMessage = chatRepository.getMessage(chat.chat.id, agentMessage.id)

    assertNull(deletedUserMessage)
    assertNull(deletedAgentMessage)
  }
}
