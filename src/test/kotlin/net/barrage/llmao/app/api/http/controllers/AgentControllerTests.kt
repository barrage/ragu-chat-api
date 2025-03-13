package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.userAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AgentControllerTests : IntegrationTest() {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration

  @BeforeAll
  fun setup() {
    runBlocking {
      agent = postgres.testAgent()
      agentConfiguration = postgres.testAgentConfiguration(agent.id)
    }
  }

  @Test
  fun listingAgentsWorksDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.get("/agents") { header(HttpHeaders.Cookie, userAccessToken()) }
    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Agent>>()
    assertNotNull(body)
    assertEquals(1, body.total)
  }

  @Test
  fun getAgentById() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/agents/${agent.id}") { header(HttpHeaders.Cookie, userAccessToken()) }
    assertEquals(200, response.status.value)
    val body = response.body<Agent>()
    assertNotNull(body)
    assertEquals(agent.id, body.id)
  }
}
