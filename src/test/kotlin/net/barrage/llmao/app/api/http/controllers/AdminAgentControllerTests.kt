package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList

class AdminAgentControllerTests : IntegrationTest() {
  val agentOne: Agent = postgres!!.testAgent()
  val agentTwo: Agent = postgres!!.testAgent(active = false)
  val adminUser: User = postgres!!.testUser("foo@bar.com", admin = true)
  val peasantUser: User = postgres!!.testUser("bar@foo.com", admin = false)
  val adminSession: Session = postgres!!.testSession(adminUser.id)
  val peasantSession: Session = postgres!!.testSession(peasantUser.id)

  @AfterTest
  fun cleanup() {
    postgres!!.container.stop()
  }

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
}
