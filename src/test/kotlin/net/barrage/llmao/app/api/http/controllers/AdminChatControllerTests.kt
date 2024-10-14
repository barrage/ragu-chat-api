package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.api.http.dto.EvaluateMessageDTO
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.core.models.*
import net.barrage.llmao.core.models.common.CountedList

class AdminChatControllerTests : IntegrationTest() {
  private val user: User = postgres!!.testUser(email = "not@important.org", admin = false)
  private val userAdmin: User = postgres!!.testUser(admin = true)
  private val userSession: Session = postgres!!.testSession(user.id)
  private val userAdminSession: Session = postgres!!.testSession(userAdmin.id)
  private val agent: Agent = postgres!!.testAgent(active = true)
  private val chatOne: Chat = postgres!!.testChat(user.id, agent.id)
  private val chatTwo: Chat = postgres!!.testChat(user.id, agent.id)
  private val messageOne: Message = postgres!!.testChatMessage(chatOne.id, user.id, "First Message")
  private val messageTwo: Message =
    postgres!!.testChatMessage(chatOne.id, user.id, "Second Message")

  @AfterTest
  fun cleanup() {
    postgres!!.container.stop()
  }

  @Test
  fun shouldRetrieveAllChatsDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<Chat>>()
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
    assertEquals(messageOne.id, body[0].id)
    assertEquals(messageTwo.id, body[1].id)
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
}
