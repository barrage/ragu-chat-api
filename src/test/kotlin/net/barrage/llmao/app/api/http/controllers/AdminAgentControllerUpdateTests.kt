package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateAgentConfiguration
import net.barrage.llmao.core.models.User
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminAgentControllerUpdateTests : IntegrationTest(useWeaviate = true) {
  private lateinit var adminUser: User
  private lateinit var adminSession: Session
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration

  @BeforeAll
  fun setup() {
    adminUser = postgres.testUser("foo@bar.com", admin = true)
    agent =
      postgres.testAgent(embeddingProvider = "azure", embeddingModel = "text-embedding-ada-002")
    agentConfiguration = postgres.testAgentConfiguration(agent.id, version = 1)
    adminSession = postgres.testSession(adminUser.id)

    weaviate!!.insertTestCollection("Kusturica", 1536)
    weaviate!!.insertTestCollection("Kusturica_small", 1536)

    postgres.testAgentCollection(
      agent.id,
      "Kusturica",
      2,
      "Use the valuable information below to pass the butter.",
    )

    postgres.testAgentCollection(
      agent.id,
      "Kusturica_small",
      2,
      "Use the valuable information below to pass the butter.",
    )
  }

  @Test
  fun updatingAgentEmbeddingParametersInvalidatesAgentCollections() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateAgent =
      UpdateAgent(
        name = "TestAgentUpdated",
        embeddingProvider = "fembed",
        embeddingModel = "Xenova/bge-large-en-v1.5",
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
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)

    val agent = response.body<AgentWithConfiguration>()

    assertEquals("Xenova/bge-large-en-v1.5", agent.agent.embeddingModel)
    assertEquals("fembed", agent.agent.embeddingProvider)

    val agentResponse =
      client.get("/admin/agents/${agent.agent.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)

    val agentFull = agentResponse.body<AgentFull>()

    assertEquals("Xenova/bge-large-en-v1.5", agentFull.agent.embeddingModel)
    assertEquals("fembed", agentFull.agent.embeddingProvider)
    assertEquals(0, agentFull.collections.size)
  }
}
