package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.USER_USER
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.core.ProvidersResponse
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentChatTimeSeries
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.DashboardCounts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdministrationControllerTests : IntegrationTest() {
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
      agentOne = postgres.testAgent(active = true, name = "TestAgentOne")
      agentOneConfiguration = postgres.testAgentConfiguration(agentOne.id, llmProvider = "openai")

      agentTwo = postgres.testAgent(active = true, name = "TestAgentTwo")
      agentTwoConfiguration = postgres.testAgentConfiguration(agentTwo.id, llmProvider = "azure")

      agentThree = postgres.testAgent(active = false, name = "TestAgentThree")
      agentThreeConfiguration =
        postgres.testAgentConfiguration(agentThree.id, llmProvider = "azure")

      chatOne = postgres.testChat(USER_USER, agentOne.id)
      chatTwo = postgres.testChat(USER_USER, agentOne.id)
    }
  }

  @Test
  fun getProvidersTest() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.get("/admin/providers") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<ProvidersResponse>()

    assert(body.llm.isNotEmpty())
    assert(body.vector.isNotEmpty())
    assert(body.embedding.isNotEmpty())
  }

  @Test
  fun getListOfProviderLanguageModelsTestOpenAI() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/openai") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()

    assert(body.isNotEmpty())
  }

  @Test
  fun getListOfProviderLanguageModelsTestAzure() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/azure") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()

    assert(body.isNotEmpty())
  }

  @Test
  fun getListOfProviderLanguageModelsTestOllama() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/ollama") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()
    assertTrue(body.isNotEmpty())
  }

  @Test
  fun getDefaultDashboardCounts() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/counts") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = response.body<DashboardCounts>()

    assertEquals(3, body.agent.total)
    assertEquals(2, body.agent.active)
    assertEquals(1, body.agent.inactive)

    assertEquals(2, body.chat.total)
    assertEquals(1, body.chat.chats.size)

    assertEquals(2, body.chat.chats.find { it.agentId == agentOne.id }!!.count)
    assertNull(body.chat.chats.find { it.agentId == agentTwo.id })
    assertNull(body.chat.chats.find { it.agentId == agentThree.id })
  }

  @Test
  fun getChatHistoryCountsByAgentWeek() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=WEEK") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }
    val body = response.body<AgentChatTimeSeries>()

    assertEquals(HttpStatusCode.OK, response.status)

    assertEquals(3, body.series.size)

    assertEquals(7, body.series[agentOne.id.toString()]!!.data.size)
    assertEquals(2, body.series[agentOne.id.toString()]!!.data[LocalDate.now().toString()])

    assertEquals(7, body.series[agentTwo.id.toString()]!!.data.size)
    assertEquals(0, body.series[agentTwo.id.toString()]!!.data[LocalDate.now().toString()])

    assertEquals(7, body.series[agentThree.id.toString()]!!.data.size)
    assertEquals(0, body.series[agentThree.id.toString()]!!.data[LocalDate.now().toString()])

    assertEquals("TestAgentOne", body.legend[agentOne.id.toString()]!!)
    assertEquals("TestAgentTwo", body.legend[agentTwo.id.toString()]!!)
  }

  @Test
  fun getChatHistoryCountsByAgentMonth() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=MONTH") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }
    val body = response.body<AgentChatTimeSeries>()

    assertEquals(HttpStatusCode.OK, response.status)

    assertEquals(3, body.series.size)

    assertEquals(30, body.series[agentOne.id.toString()]!!.data.size)
    assertEquals(2, body.series[agentOne.id.toString()]!!.data[LocalDate.now().toString()])

    assertEquals(30, body.series[agentTwo.id.toString()]!!.data.size)
    assertEquals(0, body.series[agentTwo.id.toString()]!!.data[LocalDate.now().toString()])

    assertEquals(30, body.series[agentThree.id.toString()]!!.data.size)
    assertEquals(0, body.series[agentThree.id.toString()]!!.data[LocalDate.now().toString()])

    assertEquals("TestAgentOne", body.legend[agentOne.id.toString()]!!)
    assertEquals("TestAgentTwo", body.legend[agentTwo.id.toString()]!!)
  }

  @Test
  fun getChatHistoryCountsByAgentYear() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=YEAR") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }
    val body = response.body<AgentChatTimeSeries>()

    assertEquals(HttpStatusCode.OK, response.status)

    assertEquals(3, body.series.size)

    assertEquals(12, body.series[agentOne.id.toString()]!!.data.size)

    assertEquals(12, body.series[agentTwo.id.toString()]!!.data.size)

    assertEquals(12, body.series[agentThree.id.toString()]!!.data.size)

    assertEquals("TestAgentOne", body.legend[agentOne.id.toString()]!!)
    assertEquals("TestAgentTwo", body.legend[agentTwo.id.toString()]!!)
  }
}
