package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.BUCKET_AVATARS_PATH
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.CreateAgentConfiguration
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateAgentConfiguration
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AdminAgentControllerTests : IntegrationTest() {
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

  @Test
  fun listingAgentsWorksDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentWithConfiguration>>()
    assertEquals(2, body.total)
  }

  @Test
  fun listingAgentsWorksPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents?page=2&perPage=1") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentWithConfiguration>>()
    assertEquals(2, body.total)
    assertEquals(1, body.items.size)
  }

  @Test
  fun listingAgentsWorksSearchByName() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents?name=tone") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentWithConfiguration>>()
    assertEquals(1, body.total)
    assertEquals(agentOne.id, body.items.first().agent.id)
  }

  @Test
  fun listingAgentsWorksFilterByStatus() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents?active=false") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentWithConfiguration>>()
    assertEquals(1, body.total)
    assertEquals(agentTwo.id, body.items.first().agent.id)
  }

  @Test
  fun createAgentWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val createAgent =
      CreateAgent(
        name = "TestAgentCreated",
        description = "description",
        active = true,
        language = "english",
        configuration =
          CreateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4",
            temperature = 0.5,
            instructions =
              AgentInstructions(titleInstruction = "title", summaryInstruction = "summary"),
          ),
      )

    val response =
      client.post("/admin/agents") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(createAgent)
      }

    assertEquals(201, response.status.value)
    val body = response.body<AgentWithConfiguration>()
    assertEquals("TestAgentCreated", body.agent.name)
    assertEquals("description", body.agent.description)
    assertEquals("azure", body.configuration.llmProvider)
    assertEquals("gpt-4", body.configuration.model)
    assertEquals("title", body.configuration.agentInstructions.titleInstruction)
    assertEquals("summary", body.configuration.agentInstructions.summaryInstruction)

    postgres.deleteTestAgent(body.agent.id)
  }

  @Test
  fun createAgentFailsWrongLLmProvider() = test {
    val client = createClient {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val createAgent =
      CreateAgent(
        name = "TestAgentCreated",
        description = "description",
        configuration =
          CreateAgentConfiguration(
            context = "context",
            llmProvider = "openaai",
            model = "mistral:latest",
            temperature = 0.5,
          ),
        active = true,
        language = "english",
      )

    val response =
      client.post("/admin/agents") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(createAgent)
      }

    assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.errorType)
    assertEquals(ErrorReason.InvalidProvider, body.errorReason)
    assertEquals("Unsupported LLM provider 'openaai'", body.errorDescription)
  }

  @Test
  fun getAgentWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentFull>()
    assertEquals(agentOne.id, body.agent.id)
  }

  @Test
  fun updateAgentWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = "description",
        active = true,
        language = "english",
        configuration =
          UpdateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4",
            temperature = 0.5,
            instructions = null,
          ),
      )

    val response =
      client.put("/admin/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentWithConfiguration>()
    assertEquals("TestAgentOneUpdated", body.agent.name)
    assertEquals(0.5, body.configuration.temperature)
    assertEquals(3, body.configuration.version)
  }

  @Test
  fun updatingAgentInstructionsWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val agent = postgres.testAgent()
    postgres.testAgentConfiguration(agent.id, version = 1)

    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = "description",
        active = true,
        language = "english",
        configuration =
          UpdateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4",
            temperature = 0.5,
            instructions =
              AgentInstructions(
                titleInstruction = "title",
                summaryInstruction = "summary",
                errorMessage = "error",
              ),
          ),
      )

    val response =
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)

    val body = response.body<AgentWithConfiguration>()

    assertEquals("TestAgentOneUpdated", body.agent.name)
    assertEquals("description", body.agent.description)
    assert(body.agent.active)
    assertEquals("english", body.agent.language)
    assertEquals("context", body.configuration.context)
    assertEquals("azure", body.configuration.llmProvider)
    assertEquals("gpt-4", body.configuration.model)
    assertEquals(0.5, body.configuration.temperature)
    assertEquals(2, body.configuration.version)
    assertEquals("title", body.configuration.agentInstructions.titleInstruction)
    assertEquals("summary", body.configuration.agentInstructions.summaryInstruction)
    assertEquals("error", body.configuration.agentInstructions.errorMessage)
  }

  @Test
  fun updatingAgentMetadataDoesNotBumpAgentVersion() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val agent = postgres.testAgent()
    postgres.testAgentConfiguration(agent.id, version = 1)

    val updateAgent =
      UpdateAgent(
        name = "MyAgent",
        description = "description",
        active = true,
        language = "english",
      )

    val response =
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)

    val body = response.body<AgentWithConfiguration>()

    assertEquals("MyAgent", body.agent.name)
    assertEquals("description", body.agent.description)
    assert(body.agent.active)
    assertEquals("english", body.agent.language)
    assertEquals(1, body.configuration.version)

    postgres.deleteTestAgent(agent.id)
  }

  @Test
  fun updateAgentNameSameConfigurationSameVersionWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = "description",
        active = true,
        language = "english",
        configuration =
          UpdateAgentConfiguration(
            context = "Test",
            llmProvider = "openai",
            model = "gpt-4",
            temperature = 0.1,
            instructions = null,
          ),
      )

    val response =
      client.put("/admin/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentWithConfiguration>()
    assertEquals("TestAgentOneUpdated", body.agent.name)
    assertEquals(2, body.configuration.version)
    assertEquals(0.1, body.configuration.temperature)
  }

  @Test
  fun updateAgentFailsNotFound() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = "description",
        active = true,
        language = "english",
        configuration =
          UpdateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4",
            temperature = 0.5,
            instructions = null,
          ),
      )

    val response =
      client.put("/admin/agents/${KUUID.randomUUID()}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(404, response.status.value)
    assertEquals("API", response.body<AppError>().errorType)
    assertEquals(ErrorReason.EntityDoesNotExist, response.body<AppError>().errorReason)
  }

  @Test
  fun deleteAgentWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/admin/agents/${agentTwo.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun deleteAgentFailsForActiveAgent() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("/admin/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(400, response.status.value)
    assertEquals("API", response.body<AppError>().errorType)
    assertEquals(ErrorReason.InvalidParameter, response.body<AppError>().errorReason)
    assertEquals(
      "Cannot delete active agent or agent not found",
      response.body<AppError>().errorDescription,
    )
  }

  @Test
  fun getAgentVersionsWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents/${agentOne.id}/versions?sortOrder=desc") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentConfiguration>>()
    assertEquals(2, body.total)
    assertEquals(2, body.items.size)
    assertEquals(2, body.items[0].version)
    assertEquals(agentOneConfigurationV2.id, body.items[0].id)
    assertEquals(1, body.items[1].version)
    assertEquals(agentOneConfigurationV1.id, body.items[1].id)
  }

  @Test
  fun getAgentVersionWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents/${agentOne.id}/versions/${agentOneConfigurationV1.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentConfigurationWithEvaluationCounts>()
    assertEquals(agentOneConfigurationV1.id, body.configuration.id)
    assertEquals(agentOneConfigurationV1.version, body.configuration.version)
    assertEquals(agentOneConfigurationV1.context, body.configuration.context)
    assertEquals(agentOneConfigurationV1.llmProvider, body.configuration.llmProvider)
    assertEquals(agentOneConfigurationV1.model, body.configuration.model)
    assertEquals(agentOneConfigurationV1.temperature, body.configuration.temperature)
    assertEquals(2, body.evaluationCounts.total)
    assertEquals(1, body.evaluationCounts.positive)
    assertEquals(1, body.evaluationCounts.negative)
  }

  @Test
  fun getAgentVersionEvaluatedMessagesWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents/${agentOne.id}/versions/${agentOneConfigurationV1.id}/messages") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Message>>()
    assertEquals(2, body.total)
    assertEquals("First Message", body.items.first { it.evaluation == true }.content)
    assertEquals("Second Message", body.items.first { it.evaluation == false }.content)
  }

  @Test
  fun rollbackAgentVersionWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("/admin/agents/${agentOne.id}/versions/${agentOneConfigurationV1.id}/rollback") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentWithConfiguration>()
    assertEquals(agentOne.id, body.agent.id)
    assertEquals(agentOneConfigurationV1.id, body.agent.activeConfigurationId)
    assertEquals(agentOneConfigurationV1.id, body.configuration.id)
  }

  @Test
  fun agentUploadAvatarJpeg() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(200, response.status.value)
    val body = response.body<Agent>()
    assertEquals(agentOne.id, body.id)
    assertNotNull(body.avatar, "Avatar should not be null")

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder()
          .bucket("test")
          .`object`("$BUCKET_AVATARS_PATH/${body.avatar}")
          .build()
      )
    }

    assertDoesNotThrow {
      minio.client.removeObject(
        RemoveObjectArgs.builder()
          .bucket("test")
          .`object`("$BUCKET_AVATARS_PATH/${body.avatar}")
          .build()
      )
    }
  }

  @Test
  fun agentUploadAvatarPng() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/png")
        setBody("test".toByteArray())
      }

    assertEquals(200, response.status.value)
    val body = response.body<Agent>()
    assertEquals(agentOne.id, body.id)
    assertNotNull(body.avatar, "Avatar should not be null")

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder()
          .bucket("test")
          .`object`("$BUCKET_AVATARS_PATH/${body.avatar}")
          .build()
      )
    }

    assertDoesNotThrow {
      minio.client.removeObject(
        RemoveObjectArgs.builder()
          .bucket("test")
          .`object`("$BUCKET_AVATARS_PATH/${body.avatar}")
          .build()
      )
    }
  }

  @Test
  fun agentDeleteAvatar() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val responseUpload =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(200, responseUpload.status.value)
    val bodyUpload = responseUpload.body<Agent>()
    assertEquals(agentOne.id, bodyUpload.id)
    assertNotNull(bodyUpload.avatar, "Avatar should not be null")

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder()
          .bucket("test")
          .`object`("$BUCKET_AVATARS_PATH/${bodyUpload.avatar}")
          .build()
      )
    }

    val responseDelete =
      client.delete("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, responseDelete.status.value)

    assertThrows<ErrorResponseException> {
      minio.client.statObject(
        StatObjectArgs.builder()
          .bucket("test")
          .`object`("$BUCKET_AVATARS_PATH/${bodyUpload.avatar}")
          .build()
      )
    }

    val responseCheck =
      client.get("/admin/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    val bodyCheck = responseCheck.body<AgentFull>()
    assertEquals(agentOne.id, bodyCheck.agent.id)
    assertNull(bodyCheck.agent.avatar, "Avatar should be null")
  }

  @Test
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

  @Test
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

  @Test
  fun agentAvatarUploadOverwrite() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val responseUpload1 =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }
    assertEquals(200, responseUpload1.status.value)

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${agentOne.id}.jpg").build()
      )
    }

    assertThrows<ErrorResponseException> {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${agentOne.id}.png").build()
      )
    }

    val responseUpload2 =
      client.post("/admin/agents/${agentOne.id}/avatars") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/png")
        setBody("test".toByteArray())
      }
    assertEquals(200, responseUpload2.status.value)

    assertThrows<ErrorResponseException> {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${agentOne.id}.jpg").build()
      )
    }

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${agentOne.id}.png").build()
      )
    }

    assertDoesNotThrow {
      minio.client.removeObject(
        RemoveObjectArgs.builder().bucket("test").`object`("avatars/${agentOne.id}.png").build()
      )
    }
  }
}
