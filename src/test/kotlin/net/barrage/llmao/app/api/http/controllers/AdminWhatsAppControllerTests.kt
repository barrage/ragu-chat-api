package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.app.adapters.whatsapp.dto.WhatsAppAgentUpdate
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserName
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminWhatsAppControllerTests : IntegrationTest(useWeaviate = true, enableWhatsApp = true) {
  private lateinit var adminUser: User
  private lateinit var peasantUser: User
  private lateinit var adminSession: Session
  private lateinit var peasantSession: Session
  private lateinit var whatsAppNumber: WhatsAppNumber
  private lateinit var whatsAppAgentOne: Agent
  private lateinit var whatsAppAgentTwo: Agent
  private lateinit var whatsAppAgentOneConfiguration: AgentConfiguration
  private lateinit var whatsAppAgentTwoConfiguration: AgentConfiguration
  private lateinit var whatsAppChat: WhatsAppChat
  private lateinit var whatsAppMessageOne: WhatsAppMessage
  private lateinit var whatsAppMessageTwo: WhatsAppMessage

  @BeforeAll
  fun setup() {
    runBlocking {
      adminUser = postgres.testUser("foo@bar.com", admin = true)
      peasantUser = postgres.testUser("bar@foo.com", admin = false)
      adminSession = postgres.testSession(adminUser.id)
      peasantSession = postgres.testSession(peasantUser.id)
      weaviate!!.insertTestCollection("Kusturica")
    }
  }

  @BeforeEach
  fun setupWhatsAppUser() {
    runBlocking {
      whatsAppNumber = postgres.testWhatsAppNumber(peasantUser.id, "385981234567")
      whatsAppAgentOne = postgres.testAgent("Test Whatsapp Agent 1")
      whatsAppAgentOneConfiguration =
        postgres.testAgentConfiguration(
          whatsAppAgentOne.id,
          context = "WhatsApp Test Agent Context",
        )
      whatsAppAgentTwo = postgres.testAgent("Test Whatsapp Agent 2")
      whatsAppAgentTwoConfiguration =
        postgres.testAgentConfiguration(
          whatsAppAgentTwo.id,
          context = "WhatsApp Test Agent Context",
        )
      whatsAppChat = postgres.testWhatsAppChat(peasantUser.id)
      whatsAppMessageOne = postgres.testWhatsAppMessage(whatsAppChat.id, peasantUser.id)
      whatsAppMessageTwo =
        postgres.testWhatsAppMessage(
          whatsAppChat.id,
          whatsAppAgentOne.id,
          senderType = "assistant",
          responseTo = whatsAppMessageOne.id,
        )
      postgres.setWhatsAppAgent(whatsAppAgentOne.id)
    }
  }

  @AfterEach
  fun tearDown() {
    runBlocking {
      postgres.deleteTestWhatsAppChat(whatsAppChat.id)
      postgres.deleteTestWhatsAppNumber(whatsAppNumber.id)
      postgres.deleteWhatsAppAgent()
    }
  }

  @Test
  fun successfullySetsWhatsAppAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val response = client.get("/admin/whatsapp/agent") { header("Cookie", adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<AgentFull>()
    assertEquals(whatsAppAgentOne.id, body.agent.id)
    assertEquals("Test Whatsapp Agent 1", body.agent.name)

    val responseUpdate =
      client.put("/admin/whatsapp/agent") {
        header("Cookie", adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(WhatsAppAgentUpdate(whatsAppAgentTwo.id))
      }

    assertEquals(200, responseUpdate.status.value)

    val responseGetUpdated =
      client.get("/admin/whatsapp/agent") { header("Cookie", adminAccessToken()) }

    assertEquals(200, responseGetUpdated.status.value)

    val bodyGet = responseGetUpdated.body<AgentFull>()
    assertEquals(whatsAppAgentTwo.id, bodyGet.agent.id)
    assertEquals("Test Whatsapp Agent 2", bodyGet.agent.name)
  }

  @Test
  fun failsToSetNonExistingWhatsAppAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val responseUpdate =
      client.put("/admin/whatsapp/agent") {
        header("Cookie", adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(WhatsAppAgentUpdate(KUUID.randomUUID()))
      }

    assertEquals(404, responseUpdate.status.value)
    val body = responseUpdate.body<AppError>()
    assertEquals(ErrorReason.EntityDoesNotExist, body.errorReason)
  }

  @Test
  fun adminGetWhatsAppAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.get("/admin/whatsapp/agent") { header("Cookie", adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<AgentFull>()
    assertEquals(whatsAppAgentOne.id, body.agent.id)
    assertEquals("Test Whatsapp Agent 1", body.agent.name)
  }

  @Test
  fun adminGetWhatsAppNumbersForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/whatsapp/numbers/${peasantUser.id}") {
        header("Cookie", adminAccessToken())
      }

    assertEquals(200, response.status.value)
    val body = response.body<List<WhatsAppNumber>>()
    assertEquals(1, body.size)
    assertEquals("385981234567", body[0].phoneNumber)
    assertEquals(peasantUser.id, body[0].userId)
  }

  @Test
  fun adminAddWhatsAppNumberForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/whatsapp/numbers/${peasantUser.id}") {
        header("Cookie", adminAccessToken())
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234566"))
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppNumber>()
    assertEquals("385981234566", body.phoneNumber)
    assertEquals(peasantUser.id, body.userId)

    postgres.deleteTestWhatsAppNumber(body.id)
  }

  @Test
  fun adminAddWhatsAppNumberForUserFailsDuplicate() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/whatsapp/numbers/${peasantUser.id}") {
        header("Cookie", adminAccessToken())
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234567"))
      }

    assertEquals(409, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.errorType)
    assertEquals(ErrorReason.EntityAlreadyExists, body.errorReason)
  }

  @Test
  fun adminUpdateWhatsAppNumberForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("/admin/whatsapp/numbers/${peasantUser.id}/${whatsAppNumber.id}") {
        header("Cookie", adminAccessToken())
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234569"))
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppNumber>()
    assertEquals("385981234569", body.phoneNumber)
    assertEquals(peasantUser.id, body.userId)
  }

  @Test
  fun adminDeleteWhatsAppNumberForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/admin/whatsapp/numbers/${peasantUser.id}/${whatsAppNumber.id}") {
        header("Cookie", adminAccessToken())
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun adminGetAllWhatsAppChats() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.get("/admin/whatsapp/chats") { header("Cookie", adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<WhatsAppChatWithUserName>>()
    assertEquals(1, body.total)
    assertEquals(peasantUser.id, body.items[0].chat.userId)
    assertEquals(peasantUser.fullName, body.items[0].fullName)
  }

  @Test
  fun adminGetWhatsAppChat() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/whatsapp/chats/${whatsAppChat.id}") {
        header("Cookie", adminAccessToken())
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppChatWithUserAndMessages>()
    assertEquals(whatsAppChat.id, body.chat.id)
    assertEquals(peasantUser.id, body.chat.userId)
    assertEquals(peasantUser.id, body.user.id)
    assertEquals(2, body.messages.size)
    assertEquals(whatsAppMessageOne.id, body.messages[1].id)
    assertEquals("user", body.messages[1].senderType)
    assertEquals(whatsAppMessageTwo.id, body.messages[0].id)
    assertEquals("assistant", body.messages[0].senderType)
    assertEquals(whatsAppMessageOne.id, body.messages[0].responseTo)
  }
}
