package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithAgent
import net.barrage.llmao.core.models.EvaluateMessage
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import net.barrage.llmao.utils.ValidationError
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
    runBlocking {
      user = postgres.testUser(admin = false)
      userSession = postgres.testSession(user.id)
      agent = postgres.testAgent(active = true)
      agentConfiguration = postgres.testAgentConfiguration(agent.id)
      chatOne = postgres.testChat(user.id, agent.id)
      chatTwo = postgres.testChat(user.id, agent.id)
      messageOne = postgres.testChatMessage(chatOne.id, user.id, "First Message")
      messageTwo =
        postgres.testChatMessage(
          chatOne.id,
          UUID.randomUUID(),
          "Second Message",
          senderType = "assistant",
          responseTo = messageOne.id,
        )
    }
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
    val body = response.body<Chat>()
    assertEquals(chatOne.id, body.id)
    assertEquals("Updated Title", body.title)
  }

  @Test
  fun shouldUpdateChatTitleValidationFails() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updatedTitle = UpdateChatTitleDTO("12")
    val response =
      client.put("/chats/${chatOne.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updatedTitle)
      }

    assertEquals(422, response.status.value)
    val body = response.body<List<ValidationError>>()
    assertEquals("charRange", body[0].code)
    assertEquals("Value must be between 3 - 255 characters long", body[0].message)
    assertEquals("title", body[0].fieldName)
  }

  @Test
  fun shouldDeleteChat() = test {
    val chatDelete = postgres.testChat(user.id, agent.id)
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
      client.get("/chats/${chatOne.id}/messages?page=1&perPage=25") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<List<Message>>()
    assertEquals(2, body.size)
    // Messages are sorted by createdAt DESC
    assertEquals(messageOne.id, body[1].id)
    assertEquals(messageTwo.id, body[0].id)
  }

  @Test
  fun shouldThrowErrorWhenRetrievingMessagesForNonExistingChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/chats/${UUID.randomUUID()}/messages?page=1&perPage=25") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(404, response.status.value)
  }

  @Test
  fun shouldEvaluateMessage() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessage(true)
    val response =
      client.patch("/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }

    assertEquals(200, response.status.value)
    val body = response.body<EvaluateMessage>()
    assertEquals(true, body.evaluation)
    assertEquals(null, body.feedback)
  }

  @Test
  fun shouldFailEvaluateMessageNotAssistantMessage() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessage(true)
    val response =
      client.patch("/chats/${chatOne.id}/messages/${messageOne.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }

    assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    assertEquals(ErrorReason.InvalidParameter, body.errorReason)
  }

  @Test
  fun shouldEvaluateMessageWithFeedback() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluation = EvaluateMessage(true, "good job")
    val response =
      client.patch("/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }

    assertEquals(200, response.status.value)
    val body = response.body<EvaluateMessage>()
    assertEquals(true, body.evaluation)
    assertEquals("good job", body.feedback)
  }

  @Test
  fun shouldRemoveEvaluateMessage() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val evaluationOne = EvaluateMessage(true, "what a marvelous response")
    val responseOne =
      client.patch("/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluationOne)
      }
    assertEquals(HttpStatusCode.OK, responseOne.status)

    val responseTwo =
      client.get("/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, responseTwo.status)

    val bodyOne = responseTwo.body<List<Message>>()
    assertEquals(1, bodyOne.size)
    assertEquals(messageTwo.id, bodyOne[0].id)
    assertEquals(true, bodyOne[0].evaluation)
    // users can't see feedback
    assertEquals(null, bodyOne[0].feedback)

    val evaluationTwo = EvaluateMessage(null)
    val responseThree =
      client.patch("/chats/${chatOne.id}/messages/${messageTwo.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(evaluationTwo)
      }
    assertEquals(HttpStatusCode.OK, responseThree.status)
    val bodyTwo = responseThree.body<EvaluateMessage>()
    assertEquals(null, bodyTwo.evaluation)
    assertEquals(null, bodyTwo.feedback)

    val responseFour =
      client.get("/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, responseFour.status)

    val bodyThree = responseFour.body<List<Message>>()
    assertEquals(1, bodyThree.size)
    assertEquals(messageTwo.id, bodyThree[0].id)
    assertEquals(null, bodyThree[0].evaluation)
    assertEquals(null, bodyThree[0].feedback)
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
