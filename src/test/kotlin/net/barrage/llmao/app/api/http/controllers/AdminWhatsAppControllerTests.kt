package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgent
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgentFull
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserName
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.CreateAgentConfiguration
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateCollectionAddition
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminWhatsAppControllerTests :
  IntegrationTest(useWeaviate = true, useWiremock = true, enableWhatsApp = true) {
  private lateinit var adminUser: User
  private lateinit var peasantUser: User
  private lateinit var adminSession: Session
  private lateinit var peasantSession: Session
  private lateinit var whatsAppNumber: WhatsAppNumber
  private lateinit var whatsAppAgentOne: WhatsAppAgent
  private lateinit var whatsAppAgentTwo: WhatsAppAgent
  private lateinit var whatsAppChat: WhatsAppChat
  private lateinit var whatsAppMessageOne: WhatsAppMessage
  private lateinit var whatsAppMessageTwo: WhatsAppMessage

  @BeforeAll
  fun setup() {
    adminUser = postgres.testUser("foo@bar.com", admin = true)
    peasantUser = postgres.testUser("bar@foo.com", admin = false)
    adminSession = postgres.testSession(adminUser.id)
    peasantSession = postgres.testSession(peasantUser.id)
    weaviate!!.insertTestCollection("Kusturica")
  }

  @BeforeEach
  fun setupWhatsAppUser() {
    whatsAppNumber = postgres.testWhatsAppNumber(peasantUser.id, "385981234567")
    whatsAppAgentOne = postgres.testWhatsAppAgent(active = true)
    whatsAppAgentTwo = postgres.testWhatsAppAgent(active = false)
    whatsAppChat = postgres.testWhatsAppChat(peasantUser.id)
    whatsAppMessageOne = postgres.testWhatsAppMessage(whatsAppChat.id, peasantUser.id)
    whatsAppMessageTwo =
      postgres.testWhatsAppMessage(
        whatsAppChat.id,
        whatsAppAgentOne.id,
        senderType = "assistant",
        responseTo = whatsAppMessageOne.id,
      )
  }

  @AfterEach
  fun tearDown() {
    postgres.deleteTestWhatsAppChat(whatsAppChat.id)
    postgres.deleteTestWhatsAppNumber(whatsAppNumber.id)
    postgres.deleteTestWhatsAppAgent(whatsAppAgentOne.id)
    postgres.deleteTestWhatsAppAgent(whatsAppAgentTwo.id)
  }

  /** Admin tests */
  @Test
  fun adminGetAllWhatsAppAgents() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/whatsapp/agents") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<WhatsAppAgent>>()
    assertEquals(2, body.total)
    assertEquals("Test WhatsApp Agent", body.items[0].name)
  }

  @Test
  fun adminGetAllWhatsAppAgentsFailsForNonAdmin() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/whatsapp/agents") {
        header("Cookie", sessionCookie(peasantSession.sessionId))
      }

    assertEquals(401, response.status.value)
    val body = response.body<String>()
    assertEquals("Unauthorized access", body)
  }

  @Test
  fun adminCreateWhatsAppAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val createAgent =
      CreateAgent(
        name = "Test WhatsApp Agent",
        description = "Test WhatsApp Agent Description",
        active = true,
        language = "croatian",
        configuration =
          CreateAgentConfiguration(
            context = "Test WhatsApp Agent Context",
            llmProvider = "azure",
            model = "gpt-4",
            temperature = 0.4,
            instructions =
              AgentInstructions(
                promptInstruction = "Test WhatsApp Agent Prompt Instruction",
                languageInstruction = "Test WhatsApp Agent Language Instruction",
                summaryInstruction = "Test WhatsApp Agent Summary Instruction",
              ),
          ),
      )
    val response =
      client.post("/admin/whatsapp/agents") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(createAgent)
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppAgent>()
    assertEquals("Test WhatsApp Agent", body.name)
    assertEquals("Test WhatsApp Agent Description", body.description)
    assertEquals(true, body.active)
    assertEquals("Test WhatsApp Agent Context", body.context)
    assertEquals("Test WhatsApp Agent Prompt Instruction", body.agentInstructions.promptInstruction)
    assertEquals(
      "Test WhatsApp Agent Language Instruction",
      body.agentInstructions.languageInstruction,
    )
    assertEquals(
      "Test WhatsApp Agent Summary Instruction",
      body.agentInstructions.summaryInstruction,
    )
    postgres.deleteTestWhatsAppAgent(body.id)
  }

  @Test
  fun adminGetWhatsAppAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/whatsapp/agents/${whatsAppAgentOne.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppAgentFull>()
    assertEquals("Test WhatsApp Agent", body.agent.name)
    assertEquals("Test Description", body.agent.description)
  }

  @Test
  fun adminUpdateWhatsAppAgent() = test {
    val client = createClient {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val response =
      client.put("/admin/whatsapp/agents/${whatsAppAgentOne.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          mapOf(
            "name" to "Test WhatsApp Agent Updated",
            "description" to "Test Description Updated",
          )
        )
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppAgent>()
    assertEquals("Test WhatsApp Agent Updated", body.name)
    assertEquals("Test Description Updated", body.description)
  }

  @Test
  fun adminUpdateWhatsAppAgentFailsForLastActiveAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("/admin/whatsapp/agents/${whatsAppAgentOne.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(mapOf("active" to false))
      }

    assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.type)
    assertEquals(ErrorReason.InvalidOperation, body.reason)
    assertEquals("Cannot deactivate the last active agent", body.description)
  }

  @Test
  fun adminDeleteWhatsAppAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/admin/whatsapp/agents/${whatsAppAgentTwo.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun adminDeleteWhatsAppAgentFailsForActiveAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/admin/whatsapp/agents/${whatsAppAgentOne.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(400, response.status.value)
    assertEquals("API", response.body<AppError>().type)
    assertEquals(ErrorReason.InvalidOperation, response.body<AppError>().reason)
    assertEquals("Cannot delete active agent", response.body<AppError>().description)
  }

  @Test
  fun adminUpdateWhatsAppAgentCollections() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateCollections =
      UpdateCollections(
        add =
          listOf(
            UpdateCollectionAddition(
              name = "Kusturica",
              amount = 10,
              instruction = "you pass the butter",
              provider = "weaviate",
            )
          ),
        remove = null,
      )

    val responseOne =
      client.put("/admin/whatsapp/agents/${whatsAppAgentOne.id}/collections") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(updateCollections)
      }

    assertEquals(200, responseOne.status.value)

    val responseTwo =
      client.get("/admin/whatsapp/agents/${whatsAppAgentOne.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseTwo.status.value)
    val body = responseTwo.body<WhatsAppAgentFull>()
    assertEquals(1, body.collections.size)
    assertEquals("Kusturica", body.collections[0].collection)
    assertEquals(10, body.collections[0].amount)
  }

  @Test
  fun adminRemoveCollectionFromAllWhatsAppAgents() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateCollections =
      UpdateCollections(
        add =
          listOf(
            UpdateCollectionAddition(
              name = "Kusturica",
              amount = 10,
              instruction = "you pass the butter",
              provider = "weaviate",
            )
          ),
        remove = null,
      )

    val responseOne =
      client.put("/admin/whatsapp/agents/${whatsAppAgentOne.id}/collections") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(updateCollections)
      }
    assertEquals(200, responseOne.status.value)

    val responseTwo =
      client.put("/admin/whatsapp/agents/${whatsAppAgentTwo.id}/collections") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(updateCollections)
      }
    assertEquals(200, responseTwo.status.value)

    val agentOneBefore =
      (app.adapters.adapters[WhatsAppAdapter::class] as? WhatsAppAdapter)?.getAgent(
        whatsAppAgentOne.id
      )
    assertEquals(1, agentOneBefore!!.collections.size)

    val agentTwoBefore =
      (app.adapters.adapters[WhatsAppAdapter::class] as? WhatsAppAdapter)?.getAgent(
        whatsAppAgentTwo.id
      )
    assertEquals(1, agentTwoBefore!!.collections.size)

    val response =
      client.delete("/admin/whatsapp/agents/collections?collection=Kusturica&provider=weaviate") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, response.status.value)
    val agentOneAfter =
      (app.adapters.adapters[WhatsAppAdapter::class] as? WhatsAppAdapter)?.getAgent(
        whatsAppAgentOne.id
      )
    assertEquals(0, agentOneAfter!!.collections.size)
    val agentTwoAfter =
      (app.adapters.adapters[WhatsAppAdapter::class] as? WhatsAppAdapter)?.getAgent(
        whatsAppAgentTwo.id
      )
    assertEquals(0, agentTwoAfter!!.collections.size)
  }

  @Test
  fun adminGetWhatsAppNumbersForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/whatsapp/numbers/${peasantUser.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
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
        header("Cookie", sessionCookie(adminSession.sessionId))
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
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234567"))
      }

    assertEquals(409, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.type)
    assertEquals(ErrorReason.EntityAlreadyExists, body.reason)
  }

  @Test
  fun adminUpdateWhatsAppNumberForUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("/admin/whatsapp/numbers/${peasantUser.id}/${whatsAppNumber.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
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
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun adminGetAllWhatsAppChats() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/whatsapp/chats") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

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
        header("Cookie", sessionCookie(adminSession.sessionId))
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
