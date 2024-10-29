package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.LocalDate
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.DashboardCounts
import net.barrage.llmao.core.models.LineChartKeys
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AdministrationControllerTests : IntegrationTest() {
  private lateinit var admin: User
  private lateinit var adminSession: Session
  private lateinit var userActive: User
  private lateinit var userInactive: User
  private lateinit var agentOne: Agent
  private lateinit var agentTwo: Agent
  private lateinit var agentThree: Agent
  private lateinit var chatOne: Chat
  private lateinit var chatTwo: Chat

  @BeforeAll
  fun setup() {
    admin = postgres!!.testUser(admin = true, active = true, email = "admin@barrage.net")
    adminSession = postgres!!.testSession(admin.id)
    userActive = postgres!!.testUser(admin = false, active = true, email = "user@barrage.net")
    userInactive =
      postgres!!.testUser(admin = false, active = false, email = "inactive@barrage.net")
    agentOne = postgres!!.testAgent(active = true, name = "TestAgentOne")
    agentTwo = postgres!!.testAgent(active = true, name = "TestAgentTwo")
    agentThree = postgres!!.testAgent(active = false, name = "TestAgentThree")
    chatOne = postgres!!.testChat(userActive.id, agentOne.id)
    chatTwo = postgres!!.testChat(userActive.id, agentOne.id)
  }

  @Test
  fun getProvidersTest() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<ProvidersResponse>()

    assertEquals(body.auth, listOf("google"))

    assertEquals(body.llm, listOf("openai", "azure", "ollama"))

    assertEquals(body.vector, listOf("weaviate"))

    assertEquals(body.embedding, listOf("azure", "fembed"))
  }

  @Test
  fun getListOfProviderLanguageModelsTestOpenAI() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/openai") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()

    assertEquals(body, listOf("gpt-3.5-turbo", "gpt-4"))
  }

  @Test
  fun getListOfProviderLanguageModelsTestAzure() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/azure") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()

    assertEquals(body, listOf("gpt-3.5-turbo", "gpt-4"))
  }

  @Disabled
  @Test
  fun getListOfProviderLanguageModelsTestOllama() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/ollama") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()
    assertTrue(body.isNotEmpty())
  }

  @Test
  fun getDefaultDashboardCounts() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/counts") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<DashboardCounts>()
    assertEquals(body.agent.total, 3)
    assertEquals(body.agent.active, 2)
    assertEquals(body.agent.inactive, 1)
    assertEquals(body.agent.providers.first { it.name == agentOne.llmProvider }.value, 2)
    assertEquals(body.user.total, 3)
    assertEquals(body.user.active, 2)
    assertEquals(body.user.inactive, 1)
    assertEquals(body.user.admin, 1)
    assertEquals(body.user.user, 1)
    assertEquals(body.chat.total, 2)
    assertEquals(body.chat.agents.first { it.name == agentOne.name }.value, 2)
    assertNull(body.chat.agents.firstOrNull { it.name == agentTwo.name })
    assertNull(body.chat.agents.firstOrNull { it.name == agentThree.name })
  }

  @Test
  fun getChatHistoryCountsByAgentWeek() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=WEEK") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<LineChartKeys>>()

    assertEquals(3, body.size)

    assertEquals(7, body.first { it.name == agentOne.name }.data.size)
    assertEquals(
      2,
      body
        .first { it.name == agentOne.name }
        .data
        .first { it.name == LocalDate.now().toString() }
        .value,
    )
    assertEquals(7, body.first { it.name == agentTwo.name }.data.size)

    assertEquals(
      0,
      body
        .first { it.name == agentTwo.name }
        .data
        .first { it.name == LocalDate.now().toString() }
        .value,
    )
    assertEquals(7, body.first { it.name == "Total" }.data.size)

    assertEquals(
      2,
      body.first { it.name == "Total" }.data.first { it.name == LocalDate.now().toString() }.value,
    )

    assertThrows<NoSuchElementException> { body.first { it.name == agentThree.name } }
  }

  @Test
  fun getChatHistoryCountsByAgentMonth() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=MONTH") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<LineChartKeys>>()

    assertEquals(3, body.size)
    assertEquals(30, body.first { it.name == agentOne.name }.data.size)
  }

  @Test
  fun getChatHistoryCountsByAgentYear() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=YEAR") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<LineChartKeys>>()

    assertEquals(3, body.size)
    assertEquals(12, body.first { it.name == agentOne.name }.data.size)
  }
}
