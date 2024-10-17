package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.CollectionItem
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class AdminAgentControllerTests : IntegrationTest() {
  val agentOne: Agent = postgres.testAgent()
  val agentTwo: Agent = postgres.testAgent(active = false)
  val adminUser: User = postgres.testUser("foo@bar.com", admin = true)
  val peasantUser: User = postgres.testUser("bar@foo.com", admin = false)
  val adminSession: Session = postgres.testSession(adminUser.id)
  val peasantSession: Session = postgres.testSession(peasantUser.id)
  val testCollection = weaviate.insertTestCollection("kusturica")

  @Test
  fun listingAgentsWorksDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/agents") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Agent>>()
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
    val body = response.body<CountedList<Agent>>()
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
    val body = response.body<CountedList<Agent>>()
    assertEquals(2, body.total)
  }

  @Test
  fun createAgentWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val createAgent =
      CreateAgent(
        name = "TestAgentCreated",
        description = "description",
        context = "context",
        llmProvider = "azure",
        model = "gpt-4",
        temperature = 0.5,
        vectorProvider = "weaviate",
        language = "eng",
        active = true,
        embeddingProvider = "azure",
        embeddingModel = "text-embedding-ada-002",
      )

    val response =
      client.post("/admin/agents") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(createAgent)
      }

    assertEquals(201, response.status.value)
    val body = response.body<Agent>()
    assertEquals("azure", body.llmProvider)
    assertEquals("gpt-4", body.model)
    assertEquals("weaviate", body.vectorProvider)
    assertEquals("azure", body.embeddingProvider)
    assertEquals("text-embedding-ada-002", body.embeddingModel)
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
        context = "context",
        llmProvider = "openaai",
        model = "mistral:latest",
        temperature = 0.5,
        vectorProvider = "weaviate",
        language = "eng",
        active = true,
        embeddingProvider = "fembed",
        embeddingModel = "Xenova/bge-large-en-v1.5",
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
        context = "context",
        llmProvider = "azure",
        model = "gpt-4",
        temperature = 0.5,
        vectorProvider = "weaviate",
        language = "eng",
        active = true,
        embeddingProvider = "azure",
        embeddingModel = "text-embedding-ada-002",
      )

    val response =
      client.put("/admin/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)
    val body = response.body<Agent>()
    assertEquals("TestAgentUpdated", body.name)
  }

  @Test
  fun updateAgentCollectionsWorks() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateCollections =
      UpdateCollections(
        provider = "weaviate",
        add =
          listOf(
            CollectionItem(name = "kusturica", amount = 10, instruction = "you pass the butter")
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
  }
}
