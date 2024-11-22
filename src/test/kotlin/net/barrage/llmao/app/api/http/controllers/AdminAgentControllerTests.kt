package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.CollectionItem
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.CreateAgentConfiguration
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateAgentConfiguration
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.UpdateCollectionsResult
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminAgentControllerTests : IntegrationTest(useWeaviate = true) {
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
    adminUser = postgres.testUser("foo@bar.com", admin = true)
    peasantUser = postgres.testUser("bar@foo.com", admin = false)
    agentOne =
      postgres.testAgent(embeddingProvider = "azure", embeddingModel = "text-embedding-ada-002")
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
    agentTwo = postgres.testAgent(active = false)
    agentTwoConfiguration = postgres.testAgentConfiguration(agentTwo.id)
    adminSession = postgres.testSession(adminUser.id)
    peasantSession = postgres.testSession(peasantUser.id)
    weaviate!!.insertTestCollection("Kusturica", 1536)
    weaviate!!.insertTestCollection("Kusturica_small", 1536)
    weaviate!!.insertTestCollection("Kusturica_big", 3072)
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
    assertEquals(1, body.total)
  }

  @Test
  fun listingAgentsWorksPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents?page=2&perPage=1&showDeactivated=true") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentWithConfiguration>>()
    assertEquals(2, body.total)
    assertEquals(1, body.items.size)
  }

  @Test
  fun listingAgentsWorksDefaultPaginationIncludeInactive() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents?showDeactivated=true") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentWithConfiguration>>()
    assertEquals(2, body.total)
  }

  @Test
  fun createAgentWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val createAgent =
      CreateAgent(
        name = "TestAgentCreated",
        description = "description",
        active = true,
        vectorProvider = "weaviate",
        embeddingProvider = "azure",
        embeddingModel = "text-embedding-ada-002",
        language = "english",
        configuration =
          CreateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4",
            temperature = 0.5,
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
    assertEquals("weaviate", body.agent.vectorProvider)
    assertEquals("azure", body.agent.embeddingProvider)
    assertEquals("text-embedding-ada-002", body.agent.embeddingModel)
    assertEquals("azure", body.configuration.llmProvider)
    assertEquals("gpt-4", body.configuration.model)

    postgres.deleteTestAgent(body.agent.id)
  }

  @Test
  fun createAgentFailsWrongProvider() = test {
    val client = createClient {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val createAgent =
      CreateAgent(
        name = "TestAgentCreated",
        description = "description",
        vectorProvider = "weaviate",
        embeddingProvider = "fembed",
        embeddingModel = "Xenova/bge-large-en-v1.5",
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
    assertEquals("API", body.type)
    assertEquals(ErrorReason.InvalidProvider, body.reason)
    assertEquals("Unsupported LLM provider 'openaai'", body.description)
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
        name = "TestAgentUpdated",
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
    assertEquals("TestAgentUpdated", body.agent.name)
    assertEquals(3, body.configuration.version)
  }

  @Test
  fun updateAgentCollectionsWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateCollections =
      UpdateCollections(
        provider = "weaviate",
        add =
          listOf(
            CollectionItem(
              name = "Kusturica_small",
              amount = 10,
              instruction = "you pass the butter",
            )
          ),
        remove = null,
      )

    val response =
      client.put("/admin/agents/${agentOne.id}/collections") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateCollections)
      }

    assertEquals(200, response.status.value)
    val body = response.body<UpdateCollectionsResult>()
    assertEquals(1, body.added.size)
    assertEquals("Kusturica_small", body.added[0].name)
    assertEquals(0, body.removed.size)
    assertEquals(0, body.failed.size)
  }

  @Test
  fun updateAgentCollectionsFails() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateCollections =
      UpdateCollections(
        provider = "weaviate",
        add =
          listOf(
            CollectionItem(name = "Kusturica_big", amount = 10, instruction = "you pass the butter")
          ),
        remove = null,
      )

    val response =
      client.put("/admin/agents/${agentOne.id}/collections") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateCollections)
      }

    assertEquals(200, response.status.value)
    val body = response.body<UpdateCollectionsResult>()
    assertEquals(0, body.added.size)
    assertEquals(0, body.removed.size)
    assertEquals(1, body.failed.size)
    assertEquals("Kusturica_big", body.failed[0].name)
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
}
