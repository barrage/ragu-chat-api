package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AgentControllerTests : IntegrationTest() {
  private lateinit var agent: Agent
  private lateinit var user: User
  private lateinit var userSession: Session

  @BeforeAll
  fun setup() {
    agent = postgres!!.testAgent()
    user = postgres!!.testUser(admin = false)
    userSession = postgres!!.testSession(user.id)
  }

  @Test
  fun listingAgentsWorksDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/agents") { header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId)) }
    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Agent>>()
    assertNotNull(body)
    assertEquals(1, body.total)
  }

  @Test
  fun getAgentById() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/agents/${agent.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }
    assertEquals(200, response.status.value)
    val body = response.body<AgentFull>()
    assertNotNull(body)
    assertEquals(agent.id, body.agent.id)
  }
}
