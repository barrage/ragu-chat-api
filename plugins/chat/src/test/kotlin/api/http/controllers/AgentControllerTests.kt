package api.http.controllers

import Agent
import AgentConfiguration
import ChatPlugin
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.test.IntegrationTest
import net.barrage.llmao.test.userAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import testAgent
import testAgentConfiguration

class AgentControllerTests : IntegrationTest(plugin = ChatPlugin()) {
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
  fun agentIsNotListedForUserWithInsufficientPermissions() = test { client ->
    val agent = postgres.testAgent(groups = listOf("admin"))
    postgres.testAgentConfiguration(agent.id)

    val response = client.get("/agents") { header(HttpHeaders.Cookie, userAccessToken()) }
    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Agent>>()
    assertNotNull(body)

    // Includes the initial agent
    assertEquals(1, body.total)
  }

  @Test
  fun agentIsNotListedWhenItsModelIsNotAvailable() = test { client ->
    val agent = postgres.testAgent()
    postgres.testAgentConfiguration(agent.id, model = "ThatWhichDoesNotExist")

    val response = client.get("/agents") { header(HttpHeaders.Cookie, userAccessToken()) }
    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Agent>>()
    assertNotNull(body)

    // Includes the initial agent
    assertEquals(1, body.total)
  }

  @Test
  fun agentIsListedForUserWithSufficientPermissions() = test { client ->
    val response = client.get("/agents") { header(HttpHeaders.Cookie, userAccessToken()) }
    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Agent>>()
    assertNotNull(body)
    assertEquals(1, body.total)
  }

  @Test
  fun agentNotFoundForUserWithInsufficientPermissions() = test { client ->
    val agent = postgres.testAgent(groups = listOf("admin"))
    postgres.testAgentConfiguration(agent.id)

    val response =
      client.get("/agents/${agent.id}") { header(HttpHeaders.Cookie, userAccessToken()) }
    assertEquals(404, response.status.value)
  }

  @Test
  fun agentFoundForUserWithSufficientPermissions() = test { client ->
    val response =
      client.get("/agents/${agent.id}") { header(HttpHeaders.Cookie, userAccessToken()) }
    assertEquals(200, response.status.value)
    val body = response.body<Agent>()
    assertNotNull(body)
    assertEquals(agent.id, body.id)
  }
}
