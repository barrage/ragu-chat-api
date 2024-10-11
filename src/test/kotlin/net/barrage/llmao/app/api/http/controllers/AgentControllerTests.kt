package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList

class AgentControllerTests : IntegrationTest() {
  private val agentOne: Agent = postgres!!.testAgent()
  private val agentTwo: Agent = postgres!!.testAgent(active = false)
  private val user: User = postgres!!.testUser(admin = false)
  private val userSession: Session = postgres!!.testSession(user.id)

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
      client.get("/agents/${agentOne.id}") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }
    assertEquals(200, response.status.value)
    val body = response.body<AgentFull>()
    assertNotNull(body)
    assertEquals(agentOne.id, body.agent.id)
  }
}
