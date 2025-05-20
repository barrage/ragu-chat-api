package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import jdk.internal.agent.resources.agent
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.USER_USER
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.app.workflow.chat.model.Agent
import net.barrage.llmao.app.workflow.chat.model.AgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.model.Chat
import net.barrage.llmao.app.workflow.chat.model.ChatWithMessages
import net.barrage.llmao.app.workflow.chat.whatsapp.model.WhatsAppAgentUpdate
import net.barrage.llmao.app.workflow.chat.whatsapp.model.WhatsAppNumber
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.types.KUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminWhatsAppControllerTests : IntegrationTest(useWeaviate = true, enableWhatsApp = true) {
  private lateinit var number: WhatsAppNumber
  private lateinit var whatsAppAgentOne: Agent
  private lateinit var whatsAppAgentTwo: Agent
  private lateinit var whatsAppAgentOneConfiguration: AgentConfiguration
  private lateinit var whatsAppAgentTwoConfiguration: AgentConfiguration
  private lateinit var chat: Chat
  private lateinit var messageGroupOne: MessageGroupAggregate
  private lateinit var messageGroupTwo: MessageGroupAggregate

  @BeforeAll
  fun setup() {
    runBlocking { weaviate!!.insertTestCollection("Kusturica") }
  }

  @BeforeEach
  fun setupWhatsAppUser() {
    runBlocking {
      number = postgres.testWhatsAppNumber(USER_USER, "385981234567")

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

      chat =
        postgres.testChat(
          USER_USER,
          whatsAppAgentOne.id,
          whatsAppAgentOneConfiguration.id,
          type = "WHATSAPP",
        )

      messageGroupOne = postgres.testMessagePair(chat.id, "First Message", "First Response")

      messageGroupTwo = postgres.testMessagePair(chat.id, "Second Message", "Second Response")

      postgres.setWhatsAppAgent(whatsAppAgentOne.id)
    }
  }

  @AfterEach
  fun tearDown() {
    runBlocking {
      postgres.deleteTestChat(chat.id)
      postgres.deleteTestWhatsAppNumber(number.id)
      postgres.deleteWhatsAppAgent()
    }
  }

  @Test
  fun successfullySetsWhatsAppAgent() = test { client ->
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
  fun failsToSetNonExistingWhatsAppAgent() = test { client ->
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
  fun adminGetWhatsAppAgent() = test { client ->
    val response = client.get("/admin/whatsapp/agent") { header("Cookie", adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<AgentFull>()
    assertEquals(whatsAppAgentOne.id, body.agent.id)
    assertEquals("Test Whatsapp Agent 1", body.agent.name)
  }

  @Test
  fun adminGetWhatsAppNumbersForUser() = test { client ->
    val response =
      client.get("/admin/whatsapp/numbers/${USER_USER.id}") { header("Cookie", adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<List<WhatsAppNumber>>()
    assertEquals(1, body.size)
    assertEquals("385981234567", body[0].phoneNumber)
    assertEquals(USER_USER.id, body[0].userId)
  }

  @Test
  fun adminAddWhatsAppNumberForUser() = test { client ->
    val response =
      client.post("/admin/whatsapp/numbers/${USER_USER.id}") {
        header("Cookie", adminAccessToken())
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234566", "username" to USER_USER.username))
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppNumber>()
    assertEquals("385981234566", body.phoneNumber)
    assertEquals(USER_USER.id, body.userId)

    postgres.deleteTestWhatsAppNumber(body.id)
  }

  @Test
  fun adminAddWhatsAppNumberForUserFailsDuplicate() = test { client ->
    val response =
      client.post("/admin/whatsapp/numbers/${USER_USER.id}") {
        header("Cookie", adminAccessToken())
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234567", "username" to USER_USER.username))
      }

    assertEquals(409, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.errorType)
    assertEquals(ErrorReason.EntityAlreadyExists, body.errorReason)
  }

  @Test
  fun adminUpdateWhatsAppNumberForUser() = test { client ->
    val response =
      client.put("/admin/whatsapp/numbers/${USER_USER.id}/${number.id}") {
        header("Cookie", adminAccessToken())
        header("Content-Type", "application/json")
        setBody(mapOf("phoneNumber" to "385981234569"))
      }

    assertEquals(200, response.status.value)
    val body = response.body<WhatsAppNumber>()
    assertEquals("385981234569", body.phoneNumber)
    assertEquals(USER_USER.id, body.userId)
  }

  @Test
  fun adminDeleteWhatsAppNumberForUser() = test { client ->
    val response =
      client.delete("/admin/whatsapp/numbers/${USER_USER.id}/${number.id}") {
        header("Cookie", adminAccessToken())
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun adminGetAllWhatsAppChats() = test { client ->
    val response = client.get("/admin/whatsapp/chats") { header("Cookie", adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Chat>>()
    assertEquals(1, body.total)
    assertEquals(USER_USER.id, body.items[0].userId)
    assertEquals(USER_USER.username, body.items[0].username)
  }

  @Test
  fun adminGetWhatsAppChat() = test { client ->
    val response =
      client.get("/admin/whatsapp/chats/${chat.id}") { header("Cookie", adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<ChatWithMessages>()

    assertEquals(chat.id, body.chat.id)
    assertEquals(USER_USER.id, body.chat.userId)
    assertEquals(USER_USER.username, body.chat.username)

    assertEquals(2, body.messages.items.size)

    val checkGroupOne = body.messages.items[0]
    val checkGroupTwo = body.messages.items[1]

    assertEquals(messageGroupOne.messages[0].id, checkGroupOne.messages[0].id)
    assertEquals(messageGroupOne.messages[0].content, checkGroupOne.messages[0].content)
    assertEquals(messageGroupOne.messages[0].senderType, checkGroupOne.messages[0].senderType)

    assertEquals(messageGroupOne.messages[1].id, checkGroupOne.messages[1].id)
    assertEquals(messageGroupOne.messages[1].content, checkGroupOne.messages[1].content)
    assertEquals(messageGroupOne.messages[1].senderType, checkGroupOne.messages[1].senderType)

    assertEquals(messageGroupTwo.messages[0].id, checkGroupTwo.messages[0].id)
    assertEquals(messageGroupTwo.messages[0].content, checkGroupTwo.messages[0].content)
    assertEquals(messageGroupTwo.messages[0].senderType, checkGroupTwo.messages[0].senderType)

    assertEquals(messageGroupTwo.messages[1].id, checkGroupTwo.messages[1].id)
    assertEquals(messageGroupTwo.messages[1].content, checkGroupTwo.messages[1].content)
    assertEquals(messageGroupTwo.messages[1].senderType, checkGroupTwo.messages[1].senderType)
  }
}
