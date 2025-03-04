package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll

class AdminAgentAvatarControllerTests : IntegrationTest(useMinio = true) {
  private lateinit var agentOne: Agent
  private lateinit var agentOneConfigurationV1: AgentConfiguration
  private lateinit var agentOneConfigurationV2: AgentConfiguration
  private lateinit var agentOneChat: Chat
  private lateinit var chatPositiveMessage: Message
  private lateinit var chatNegativeMessage: Message
  private lateinit var agentTwo: Agent
  private lateinit var agentTwoConfiguration: AgentConfiguration
  private lateinit var adminUser: User
  private lateinit var peasantUser: User
  private lateinit var adminSession: Session
  private lateinit var peasantSession: Session

  @BeforeAll
  fun setup() {
    runBlocking {
      adminUser = postgres.testUser("foo@bar.com", admin = true)
      peasantUser = postgres.testUser("bar@foo.com", admin = false)
      agentOne = postgres.testAgent(name = "TestAgentOne")
      agentOneConfigurationV1 = postgres.testAgentConfiguration(agentOne.id, version = 1)
      agentOneConfigurationV2 = postgres.testAgentConfiguration(agentOne.id, version = 2)
      agentOneChat = postgres.testChat(peasantUser.id, agentOne.id)
      val chatMessageOne =
        postgres.testChatMessage(agentOneChat.id, peasantUser.id, "First Message", "user")
      chatPositiveMessage =
        postgres.testChatMessage(
          agentOneChat.id,
          agentOneConfigurationV1.id,
          "First Message",
          "assistant",
          responseTo = chatMessageOne.id,
          evaluation = true,
        )
      val chatMessageTwo =
        postgres.testChatMessage(agentOneChat.id, peasantUser.id, "Second Message", "user")
      chatNegativeMessage =
        postgres.testChatMessage(
          agentOneChat.id,
          agentOneConfigurationV1.id,
          "Second Message",
          "assistant",
          responseTo = chatMessageTwo.id,
          evaluation = false,
        )
      agentTwo = postgres.testAgent(name = "TestAgentTwo", active = false)
      agentTwoConfiguration = postgres.testAgentConfiguration(agentTwo.id)
      adminSession = postgres.testSession(adminUser.id)
      peasantSession = postgres.testSession(peasantUser.id)
    }
  }

  @org.junit.jupiter.api.Test
  fun agentUploadAvatarJpeg() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)

    val responseCheck =
      client.get("/avatars/${agentOne.id}.jpeg") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/jpeg", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @org.junit.jupiter.api.Test
  fun agentUploadAvatarPng() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/png")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)
    val body = response.bodyAsText()
    assertEquals("${agentOne.id}.png", body)

    val responseCheck =
      client.get("/avatars/${agentOne.id}.png") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/png", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @org.junit.jupiter.api.Test
  fun agentDeleteAvatar() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val responseUpload =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, responseUpload.status.value)

    val responseDelete =
      client.delete("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, responseDelete.status.value)

    val responseCheck =
      client.get("/admin/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)

    val bodyCheck = responseCheck.body<AgentFull>()

    assertEquals(agentOne.id, bodyCheck.agent.id)
    assertNull(bodyCheck.agent.avatar, "Avatar should be null")

    val responseCheckAvatar =
      client.get("/avatars/${agentOne.id}.jpeg") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(404, responseCheckAvatar.status.value)
  }

  @org.junit.jupiter.api.Test
  fun agentFailUploadAvatarWrongContentType() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "text/plain")
        setBody("test".toByteArray())
      }

    assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.errorType)
    assertEquals(ErrorReason.InvalidContentType, body.errorReason)
  }

  @org.junit.jupiter.api.Test
  fun agentFailUploadAvatarTooLarge() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test123".repeat(1000000).toByteArray())
      }

    assertEquals(413, response.status.value)
  }

  @org.junit.jupiter.api.Test
  fun agentAvatarUploadOverwrite() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val responseUploadOriginal =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("foo".toByteArray())
      }

    assertEquals(201, responseUploadOriginal.status.value)

    val responseCheck =
      client.get("/avatars/${agentOne.id}.jpeg") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/jpeg", responseCheck.headers["Content-Type"])
    assertEquals("foo", responseCheck.bodyAsText())

    val responseUploadOverwrite =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/png")
        setBody("bar".toByteArray())
      }

    assertEquals(201, responseUploadOverwrite.status.value)

    val responseCheckOverwrite =
      client.get("/avatars/${agentOne.id}.png") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheckOverwrite.status.value)
    assertEquals("image/png", responseCheckOverwrite.headers["Content-Type"])
    assertEquals("bar", responseCheckOverwrite.bodyAsText())
  }
}
