package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.barrage.llmao.TestClass

class AdministrationControllerTests : TestClass() {
  private val user = postgres!!.testUser(admin = true)
  private val userSession = postgres!!.testSession(user.id)

  @Test
  fun getProvidersTest() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<Map<String, List<String>>>()

    assertTrue(body.containsKey("auth"))
    assertEquals(body["auth"]!!, listOf("google"))

    assertTrue(body.containsKey("llm"))
    assertEquals(body["llm"]!!, listOf("openai", "azure", "ollama"))

    assertTrue(body.containsKey("vector"))
    assertEquals(body["vector"]!!, listOf("weaviate"))

    assertTrue(body.containsKey("embedding"))
    assertEquals(body["embedding"]!!, listOf("azure", "fembed"))
  }

  @Test
  fun getListOfProviderLanguageModelsTestOpenAI() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/openai") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
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
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()

    assertEquals(body, listOf("gpt-3.5-turbo", "gpt-4"))
  }

  @Test
  fun getListOfProviderLanguageModelsTestOllama() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers/llm/ollama") {
        header(HttpHeaders.Cookie, sessionCookie(userSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<List<String>>()
    assertTrue(body.isNotEmpty())
  }
}
