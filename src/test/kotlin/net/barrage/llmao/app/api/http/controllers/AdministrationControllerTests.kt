package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentChatTimeSeries
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.DashboardCounts
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdministrationControllerTests : IntegrationTest(useWiremock = true) {
  private lateinit var admin: User
  private lateinit var adminSession: Session
  private lateinit var userActive: User
  private lateinit var userInactive: User
  private lateinit var agentOne: Agent
  private lateinit var agentOneConfiguration: AgentConfiguration
  private lateinit var agentTwo: Agent
  private lateinit var agentTwoConfiguration: AgentConfiguration
  private lateinit var agentThree: Agent
  private lateinit var agentThreeConfiguration: AgentConfiguration
  private lateinit var chatOne: Chat
  private lateinit var chatTwo: Chat

  @BeforeAll
  fun setup() {
    runBlocking {
      admin = postgres.testUser(admin = true, active = true, email = "admin@barrage.net")
      adminSession = postgres.testSession(admin.id)

      userActive = postgres.testUser(admin = false, active = true, email = "user@barrage.net")
      userInactive =
        postgres.testUser(admin = false, active = false, email = "inactive@barrage.net")

      agentOne = postgres.testAgent(active = true, name = "TestAgentOne")
      agentOneConfiguration = postgres.testAgentConfiguration(agentOne.id, llmProvider = "openai")

      agentTwo = postgres.testAgent(active = true, name = "TestAgentTwo")
      agentTwoConfiguration = postgres.testAgentConfiguration(agentTwo.id, llmProvider = "azure")

      agentThree = postgres.testAgent(active = false, name = "TestAgentThree")
      agentThreeConfiguration =
        postgres.testAgentConfiguration(agentThree.id, llmProvider = "azure")

      chatOne = postgres.testChat(userActive.id, agentOne.id)
      chatTwo = postgres.testChat(userActive.id, agentOne.id)
    }
  }

  @Test
  fun getProvidersTest() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    Assertions.assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<ProvidersResponse>()

    Assertions.assertEquals(body.auth.size, 3)
    Assertions.assertTrue(body.auth.contains("google"))
    Assertions.assertTrue(body.auth.contains("apple"))
    Assertions.assertTrue(body.auth.contains("carnet"))

    Assertions.assertEquals(body.llm, listOf("openai", "azure", "ollama"))

    Assertions.assertEquals(body.vector, listOf("weaviate"))

    Assertions.assertEquals(body.embedding, listOf("openai", "azure", "fembed"))
  }

  @Test
  fun getListOfProviderLanguageModelsTestOpenAI() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/openai") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    Assertions.assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()

    Assertions.assertEquals(body, listOf("gpt-3.5-turbo", "gpt-4", "gpt-4o"))
  }

  @Test
  fun getListOfProviderLanguageModelsTestAzure() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/azure") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    Assertions.assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()

    Assertions.assertEquals(body, listOf("gpt-3.5-turbo", "gpt-4", "gpt-4o"))
  }

  @Test
  fun getListOfProviderLanguageModelsTestOllama() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/ollama") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    Assertions.assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()
    Assertions.assertTrue(body.isNotEmpty())
  }

  @Test
  fun getDefaultDashboardCounts() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/counts") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    Assertions.assertEquals(HttpStatusCode.OK, response.status)

    val body = response.body<DashboardCounts>()

    Assertions.assertEquals(3, body.agent.total)
    Assertions.assertEquals(2, body.agent.active)
    Assertions.assertEquals(1, body.agent.inactive)
    Assertions.assertEquals(1, body.agent.providers["openai"])
    Assertions.assertEquals(1, body.agent.providers["azure"])

    Assertions.assertEquals(4, body.user.total)
    Assertions.assertEquals(3, body.user.active)
    Assertions.assertEquals(1, body.user.inactive)
    Assertions.assertEquals(2, body.user.admin)
    Assertions.assertEquals(1, body.user.user)

    Assertions.assertEquals(2, body.chat.total)
    Assertions.assertEquals(1, body.chat.chats.size)

    Assertions.assertEquals(2, body.chat.chats.find { it.agentId == agentOne.id }!!.count)
    Assertions.assertNull(body.chat.chats.find { it.agentId == agentTwo.id })
    Assertions.assertNull(body.chat.chats.find { it.agentId == agentThree.id })
  }

  @Test
  fun getChatHistoryCountsByAgentWeek() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=WEEK") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }
    val body = response.body<AgentChatTimeSeries>()

    Assertions.assertEquals(HttpStatusCode.OK, response.status)

    Assertions.assertEquals(3, body.series.size)

    Assertions.assertEquals(7, body.series[agentOne.id.toString()]!!.data.size)
    Assertions.assertEquals(
      2,
      body.series[agentOne.id.toString()]!!.data[LocalDate.now().toString()],
    )

    Assertions.assertEquals(7, body.series[agentTwo.id.toString()]!!.data.size)
    Assertions.assertEquals(
      0,
      body.series[agentTwo.id.toString()]!!.data[LocalDate.now().toString()],
    )

    Assertions.assertEquals(7, body.series[agentThree.id.toString()]!!.data.size)
    Assertions.assertEquals(
      0,
      body.series[agentThree.id.toString()]!!.data[LocalDate.now().toString()],
    )

    Assertions.assertEquals("TestAgentOne", body.legend[agentOne.id.toString()]!!)
    Assertions.assertEquals("TestAgentTwo", body.legend[agentTwo.id.toString()]!!)
  }

  @Test
  fun getChatHistoryCountsByAgentMonth() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=MONTH") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }
    val body = response.body<AgentChatTimeSeries>()

    Assertions.assertEquals(HttpStatusCode.OK, response.status)

    Assertions.assertEquals(3, body.series.size)

    Assertions.assertEquals(30, body.series[agentOne.id.toString()]!!.data.size)
    Assertions.assertEquals(
      2,
      body.series[agentOne.id.toString()]!!.data[LocalDate.now().toString()],
    )

    Assertions.assertEquals(30, body.series[agentTwo.id.toString()]!!.data.size)
    Assertions.assertEquals(
      0,
      body.series[agentTwo.id.toString()]!!.data[LocalDate.now().toString()],
    )

    Assertions.assertEquals(30, body.series[agentThree.id.toString()]!!.data.size)
    Assertions.assertEquals(
      0,
      body.series[agentThree.id.toString()]!!.data[LocalDate.now().toString()],
    )

    Assertions.assertEquals("TestAgentOne", body.legend[agentOne.id.toString()]!!)
    Assertions.assertEquals("TestAgentTwo", body.legend[agentTwo.id.toString()]!!)
  }

  @Test
  fun getChatHistoryCountsByAgentYear() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=YEAR") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }
    val body = response.body<AgentChatTimeSeries>()

    Assertions.assertEquals(HttpStatusCode.OK, response.status)

    Assertions.assertEquals(3, body.series.size)

    Assertions.assertEquals(12, body.series[agentOne.id.toString()]!!.data.size)

    Assertions.assertEquals(12, body.series[agentTwo.id.toString()]!!.data.size)

    Assertions.assertEquals(12, body.series[agentThree.id.toString()]!!.data.size)

    Assertions.assertEquals("TestAgentOne", body.legend[agentOne.id.toString()]!!)
    Assertions.assertEquals("TestAgentTwo", body.legend[agentTwo.id.toString()]!!)
  }
}
