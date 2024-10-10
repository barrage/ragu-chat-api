package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import junit.framework.TestCase.assertEquals
import kotlin.test.Test
import net.barrage.llmao.TestClass
import net.barrage.llmao.app.api.http.dto.EvaluateMessageDTO
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.core.models.*
import net.barrage.llmao.core.models.common.CountedList

class ChatControllerTests : TestClass() {
  private val user: User = postgres!!.testUser(admin = false)
  private val userSession: Session = postgres!!.testSession(user.id)
  private val agent: Agent = postgres!!.testAgent(active = true)
  private val chatOne: Chat = postgres!!.testChat(user.id, agent.id)
  private val chatTwo: Chat = postgres!!.testChat(user.id, agent.id)
  private val messageOne: Message = postgres!!.testChatMessage(chatOne.id, user.id, "First Message")
  private val messageTwo: Message =
    postgres!!.testChatMessage(chatOne.id, user.id, "Second Message")

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
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/chats/${chatTwo.id}") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(204, response.status.value)
    val body = response.body<String>()
    assertEquals("", body)

    val check = client.get("/chats") { header("Cookie", sessionCookie(userSession.sessionId)) }

    assertEquals(200, check.status.value)
    val checkBody = check.body<CountedList<Chat>>()
    assertEquals(1, checkBody.total)
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
}
