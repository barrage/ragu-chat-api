package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.adapters.whatsapp.dto.Contact
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipMessageType
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResponseDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResult
import net.barrage.llmao.app.adapters.whatsapp.dto.Message
import net.barrage.llmao.app.adapters.whatsapp.dto.Price
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppControllerTests :
  IntegrationTest(useWeaviate = true, useWiremock = true, enableWhatsApp = true) {
  private lateinit var peasantUser: User
  private lateinit var peasantSession: Session
  private lateinit var whatsAppNumber: WhatsAppNumber
  private lateinit var whatsAppAgentOne: Agent
  private lateinit var whatsAppAgentTwo: Agent
  private lateinit var whatsAppAgentOneConfiguration: AgentConfiguration
  private lateinit var whatsAppAgentTwoConfiguration: AgentConfiguration
  private lateinit var whatsAppChat: WhatsAppChat
  private lateinit var whatsAppMessageOne: WhatsAppMessage
  private lateinit var whatsAppMessageTwo: WhatsAppMessage

  private val userNumber = "385981234567"

  @BeforeAll
  fun setup() {
    runBlocking {
      peasantUser = postgres.testUser("bar@foo.com", admin = false)
      peasantSession = postgres.testSession(peasantUser.id)
      weaviate!!.insertTestCollection("Kusturica")
    }
  }

  @BeforeEach
  fun setupWhatsAppUser() {
    runBlocking {
      whatsAppNumber = postgres.testWhatsAppNumber(peasantUser.id, userNumber)
      whatsAppAgentOne = postgres.testAgent("Agent 1")
      whatsAppAgentTwo = postgres.testAgent("Agent 2")
      whatsAppAgentOneConfiguration =
        postgres.testAgentConfiguration(
          whatsAppAgentOne.id,
          context = "WhatsApp Test Agent Context",
        )
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

  /** Webhook tests */
  @Test
  fun webhookIncomingMessage() = test {
    val infobipResponse =
      InfobipResponseDTO(
        results =
          listOf(
            InfobipResult(
              from = userNumber,
              to = "385912222222",
              integrationType = "WHATSAPP",
              receivedAt = "2024-11-28T07:57:28.000+00:00",
              messageId = "wamid.HBgLMzg1OTg2MzE4MjkVAgASGBQzQUIxNzY4MzZBOUE2MDE0QUZDMwA=",
              pairedMessageId = null,
              callbackData = null,
              message = Message(text = "Hello", type = InfobipMessageType.TEXT),
              contact = Contact(name = "385981234567"),
              price = Price(pricePerMessage = 0.0, currency = "EUR"),
            )
          ),
        messageCount = 1,
        pendingMessageCount = 0,
      )

    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/whatsapp/webhook") {
        header("Content-Type", "application/json")
        setBody(infobipResponse)
      }

    assertEquals(200, response.status.value)
  }

  /** User tests */
  @Test
  fun getWhatsAppNumbersForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/whatsapp/numbers") { header("Cookie", sessionCookie(peasantSession.sessionId)) }

    assertEquals(200, response.status.value)
    val body = response.body<List<WhatsAppNumber>>()
    assertEquals(1, body.size)
    assertEquals("385981234567", body[0].phoneNumber)
    assertEquals(peasantUser.id, body[0].userId)
  }

  @Test
  fun addWhatsAppNumberForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/whatsapp/numbers") {
        header("Cookie", sessionCookie(peasantSession.sessionId))
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234565"))
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppNumber>()
    assertEquals("385981234565", body.phoneNumber)
    assertEquals(peasantUser.id, body.userId)

    postgres.deleteTestWhatsAppNumber(body.id)
  }

  @Test
  fun updateWhatsAppNumberForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("/whatsapp/numbers/${whatsAppNumber.id}") {
        header("Cookie", sessionCookie(peasantSession.sessionId))
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234564"))
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppNumber>()
    assertEquals("385981234564", body.phoneNumber)
    assertEquals(peasantUser.id, body.userId)
  }

  @Test
  fun deleteWhatsAppNumberForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/whatsapp/numbers/${whatsAppNumber.id}") {
        header("Cookie", sessionCookie(peasantSession.sessionId))
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun getWhatsAppChatsForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/whatsapp/chats") { header("Cookie", sessionCookie(peasantSession.sessionId)) }

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
