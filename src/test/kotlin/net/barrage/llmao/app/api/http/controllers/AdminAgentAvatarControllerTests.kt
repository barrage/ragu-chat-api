package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.USER_USER
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.MessageGroupAggregate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminAgentAvatarControllerTests : IntegrationTest(useMinio = true) {
  private lateinit var agentOne: Agent
  private lateinit var agentOneConfigurationV1: AgentConfiguration
  private lateinit var agentOneConfigurationV2: AgentConfiguration
  private lateinit var agentOneChat: Chat
  private lateinit var chatPositiveMessage: MessageGroupAggregate
  private lateinit var chatNegativeMessage: MessageGroupAggregate
  private lateinit var agentTwo: Agent
  private lateinit var agentTwoConfiguration: AgentConfiguration

  @BeforeAll
  fun setup() {
    runBlocking {
      agentOne = postgres.testAgent(name = "TestAgentOne")
      agentTwo = postgres.testAgent(name = "TestAgentTwo", active = false)

      agentOneConfigurationV1 = postgres.testAgentConfiguration(agentOne.id, version = 1)
      agentOneConfigurationV2 = postgres.testAgentConfiguration(agentOne.id, version = 2)
      agentTwoConfiguration = postgres.testAgentConfiguration(agentTwo.id)

      agentOneChat = postgres.testChat(USER_USER, agentOne.id)

      chatPositiveMessage =
        postgres.testMessagePair(
          agentOneChat.id,
          agentOneConfigurationV1.id,
          "First Message",
          "First Response",
          evaluation = true,
        )

      chatNegativeMessage =
        postgres.testMessagePair(
          agentOneChat.id,
          agentOneConfigurationV1.id,
          "Second Message",
          "Second Response",
          evaluation = false,
        )
    }
  }

  @Test
  fun agentUploadAvatarJpeg() = test { client ->
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)

    val responseCheck =
      client.get("/avatars/${agentOne.id}.jpeg") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/jpeg", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @Test
  fun agentUploadAvatarPng() = test { client ->
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
        header("Content-Type", "image/png")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)
    val body = response.bodyAsText()
    assertEquals("${agentOne.id}.png", body)

    val responseCheck =
      client.get("/avatars/${agentOne.id}.png") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/png", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @Test
  fun agentDeleteAvatar() = test { client ->
    val responseUpload =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, responseUpload.status.value)

    val responseDelete =
      client.delete("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(204, responseDelete.status.value)

    val responseCheck =
      client.get("/admin/agents/${agentOne.id}") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, responseCheck.status.value)

    val bodyCheck = responseCheck.body<AgentFull>()

    assertEquals(agentOne.id, bodyCheck.agent.id)
    assertNull(bodyCheck.agent.avatar, "Avatar should be null")

    val responseCheckAvatar =
      client.get("/avatars/${agentOne.id}.jpeg") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(404, responseCheckAvatar.status.value)
  }

  @Test
  fun agentFailUploadAvatarWrongContentType() = test { client ->
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
        header("Content-Type", "text/plain")
        setBody("test".toByteArray())
      }

    assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.errorType)
    assertEquals(ErrorReason.InvalidParameter, body.errorReason)
  }

  @Test
  fun agentFailUploadAvatarTooLarge() = test { client ->
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
        header("Content-Type", "image/jpeg")
        setBody("test123".repeat(1000000).toByteArray())
      }

    assertEquals(413, response.status.value)
  }

  @Test
  fun agentAvatarUploadOverwrite() = test { client ->
    val responseUploadOriginal =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
        header("Content-Type", "image/jpeg")
        setBody("foo".toByteArray())
      }

    assertEquals(201, responseUploadOriginal.status.value)

    val responseCheck =
      client.get("/avatars/${agentOne.id}.jpeg") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/jpeg", responseCheck.headers["Content-Type"])
    assertEquals("foo", responseCheck.bodyAsText())

    val responseUploadOverwrite =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, adminAccessToken())
        header("Content-Type", "image/png")
        setBody("bar".toByteArray())
      }

    assertEquals(201, responseUploadOverwrite.status.value)

    val responseCheckOverwrite =
      client.get("/avatars/${agentOne.id}.png") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, responseCheckOverwrite.status.value)
    assertEquals("image/png", responseCheckOverwrite.headers["Content-Type"])
    assertEquals("bar", responseCheckOverwrite.bodyAsText())
  }
}
