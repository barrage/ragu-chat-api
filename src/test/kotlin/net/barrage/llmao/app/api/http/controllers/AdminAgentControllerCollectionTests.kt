package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.app.workflow.chat.model.Agent
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.core.model.UpdateCollectionAddition
import net.barrage.llmao.core.model.UpdateCollections
import net.barrage.llmao.core.model.UpdateCollectionsResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminAgentControllerCollectionTests : IntegrationTest(useWeaviate = true) {
  private lateinit var agentOne: Agent
  private lateinit var agentTwo: Agent

  @BeforeAll
  fun setup() {
    weaviate!!.insertTestCollection("Kusturica")
  }

  @BeforeEach
  fun beforeEach() {
    runBlocking {
      agentOne = postgres.testAgent()
      agentTwo = postgres.testAgent()

      postgres.testAgentConfiguration(agentOne.id, version = 1)
      postgres.testAgentConfiguration(agentTwo.id, version = 1)
    }
  }

  @Test
  fun updateAgentCollectionsWorks() = test { client ->
    val updateCollections =
      UpdateCollections(
        add =
          listOf(
            UpdateCollectionAddition(
              provider = "weaviate",
              name = "Kusturica",
              amount = 10,
              instruction = "you pass the butter",
            )
          ),
        remove = null,
      )

    val response =
      client.put("/admin/agents/${agentOne.id}/collections") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateCollections)
      }

    assertEquals(200, response.status.value)
    val body = response.body<UpdateCollectionsResult>()
    assertEquals(1, body.added.size)
    assertEquals("Kusturica", body.added[0].name)
    assertEquals(0, body.removed.size)
    assertEquals(0, body.failed.size)
  }

  @Test
  fun updateAgentCollectionFailsCollectionNotFound() = test { client ->
    val updateCollections =
      UpdateCollections(
        add =
          listOf(
            UpdateCollectionAddition(
              provider = "weaviate",
              name = "Kusturica_not_found",
              amount = 10,
              instruction = "you pass the butter",
            )
          ),
        remove = null,
      )

    val response =
      client.put("/admin/agents/${agentOne.id}/collections") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateCollections)
      }

    assertEquals(200, response.status.value)
    val body = response.body<UpdateCollectionsResult>()
    assertEquals(0, body.added.size)
    assertEquals(0, body.removed.size)
    assertEquals(1, body.failed.size)
    assertEquals("Kusturica_not_found", body.failed[0].name)
  }

  @Test
  fun removeCollectionFromAllAgents() = test { client ->
    val updateCollections =
      UpdateCollections(
        add =
          listOf(
            UpdateCollectionAddition(
              provider = "weaviate",
              name = "Kusturica",
              amount = 10,
              instruction = "you pass the butter",
            )
          ),
        remove = null,
      )

    client.put("/admin/agents/${agentOne.id}/collections") {
      header(HttpHeaders.Cookie, adminAccessToken())
      contentType(ContentType.Application.Json)
      setBody(updateCollections)
    }

    client.put("/admin/agents/${agentTwo.id}/collections") {
      header(HttpHeaders.Cookie, adminAccessToken())
      contentType(ContentType.Application.Json)
      setBody(updateCollections)
    }

    val agentOneBefore =
      client
        .get("/admin/agents/${agentOne.id}") { header("Cookie", adminAccessToken()) }
        .body<AgentFull>()

    assertEquals(1, agentOneBefore.collections.size)
    assertEquals("Kusturica", agentOneBefore.collections[0].collection)
    assertEquals(10, agentOneBefore.collections[0].amount)
    assertEquals("you pass the butter", agentOneBefore.collections[0].instruction)

    val agentTwoBefore =
      client
        .get("/admin/agents/${agentTwo.id}") { header("Cookie", adminAccessToken()) }
        .body<AgentFull>()

    assertEquals(1, agentTwoBefore.collections.size)
    assertEquals("Kusturica", agentTwoBefore.collections[0].collection)
    assertEquals(10, agentTwoBefore.collections[0].amount)
    assertEquals("you pass the butter", agentTwoBefore.collections[0].instruction)

    val response =
      client.delete("/admin/agents/collections?collection=Kusturica&provider=weaviate") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    val agentOneAfter =
      client
        .get("/admin/agents/${agentOne.id}") { header("Cookie", adminAccessToken()) }
        .body<AgentFull>()
    assertEquals(0, agentOneAfter.collections.size)

    val agentTwoAfter =
      client
        .get("/admin/agents/${agentTwo.id}") { header("Cookie", adminAccessToken()) }
        .body<AgentFull>()

    assertEquals(0, agentTwoAfter.collections.size)

    assertEquals(204, response.status.value)
  }
}
