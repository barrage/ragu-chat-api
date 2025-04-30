package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.USER_USER
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.app.workflow.chat.model.Agent
import net.barrage.llmao.app.workflow.chat.model.AgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.model.AgentInstructions
import net.barrage.llmao.app.workflow.chat.model.AgentWithConfiguration
import net.barrage.llmao.app.workflow.chat.model.Chat
import net.barrage.llmao.app.workflow.chat.model.CreateAgent
import net.barrage.llmao.app.workflow.chat.model.CreateAgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.UpdateAgent
import net.barrage.llmao.app.workflow.chat.model.UpdateAgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.UpdateAgentInstructions
import net.barrage.llmao.app.workflow.chat.routes.AgentDisplay
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.types.KUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class AdminAgentControllerTests : IntegrationTest() {
  private lateinit var agentOne: Agent
  private lateinit var agentOneConfigurationV1: AgentConfiguration
  private lateinit var agentOneConfigurationV2: AgentConfiguration
  private lateinit var agentOneChat: Chat
  private lateinit var chatPositiveMessage: MessageGroupAggregate
  private lateinit var chatNegativeMessage: MessageGroupAggregate
  private lateinit var agentTwo: Agent
  private lateinit var agentTwoConfiguration: AgentConfiguration

  @BeforeAll
  fun setup() {
    runBlocking {
      agentOne = postgres.testAgent(name = "TestAgentOne")
      agentTwo = postgres.testAgent(name = "TestAgentTwo", active = false)

      agentOneConfigurationV1 = postgres.testAgentConfiguration(agentOne.id, version = 1)
      agentOneConfigurationV2 = postgres.testAgentConfiguration(agentOne.id, version = 2)
      agentTwoConfiguration = postgres.testAgentConfiguration(agentTwo.id)

      agentOneChat = postgres.testChat(USER_USER, agentOne.id, agentOneConfigurationV1.id)

      chatPositiveMessage =
        postgres.testMessagePair(
          agentOneChat.id,
          "First Message",
          "First Response",
          evaluation = true,
        )

      chatNegativeMessage =
        postgres.testMessagePair(
          agentOneChat.id,
          "Second Message",
          "Second Response",
          evaluation = false,
        )
    }
  }

  @Test
  fun listingAgentsWorksDefaultPagination() = test { client ->
    val response = client.get("/admin/agents") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentDisplay>>()
    assertEquals(2, body.total)
  }

  @Test
  fun listingAgentsWorksPagination() = test { client ->
    val response =
      client.get("/admin/agents?page=2&perPage=1") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(200, response.status.value)

    val body = response.body<CountedList<AgentDisplay>>()

    assertEquals(2, body.total)
    assertEquals(1, body.items.size)
  }

  @Test
  fun listingAgentsWorksSearchByName() = test { client ->
    val response =
      client.get("/admin/agents?name=tone") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentDisplay>>()
    assertEquals(1, body.total)
    assertEquals(agentOne.id, body.items.first().agent.id)
  }

  @Test
  fun listingAgentsWorksFilterByStatus() = test { client ->
    val response =
      client.get("/admin/agents?active=false") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, response.status.value)

    val body = response.body<CountedList<AgentWithConfiguration>>()
    assertEquals(1, body.total)
    assertEquals(agentTwo.id, body.items.first().agent.id)
  }

  @Test
  fun createAgentWorks() = test { client ->
    val createAgent =
      CreateAgent(
        name = "TestAgentCreated",
        description = "description",
        active = true,
        language = "english",
        configuration =
          CreateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4o-mini",
            temperature = 0.5,
            presencePenalty = 0.5,
            maxCompletionTokens = 100,
            instructions = AgentInstructions(titleInstruction = "title", errorMessage = "error"),
          ),
      )

    val response =
      client.post("/admin/agents") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(createAgent)
      }

    assertEquals(201, response.status.value)
    val body = response.body<AgentWithConfiguration>()
    assertEquals("TestAgentCreated", body.agent.name)
    assertEquals("description", body.agent.description)
    assertEquals("azure", body.configuration.llmProvider)
    assertEquals("gpt-4o-mini", body.configuration.model)
    assertEquals("title", body.configuration.agentInstructions.titleInstruction)
    assertEquals(1, body.configuration.version)
    assertEquals(0.5, body.configuration.temperature)
    assertEquals(0.5, body.configuration.presencePenalty)
    assertEquals(100, body.configuration.maxCompletionTokens)
    assertEquals("error", body.configuration.agentInstructions.errorMessage)

    postgres.deleteTestAgent(body.agent.id)
  }

  @Test
  fun createAgentFailsWrongLLmProvider() = test { client ->
    val createAgent =
      CreateAgent(
        name = "TestAgentCreated",
        description = "description",
        configuration =
          CreateAgentConfiguration(
            context = "context",
            llmProvider = "openaai",
            model = "mistral:latest",
            temperature = 0.5,
          ),
        active = true,
        language = "english",
      )

    val response =
      client.post("/admin/agents") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(createAgent)
      }

    assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.errorType)
    assertEquals(ErrorReason.InvalidParameter, body.errorReason)
  }

  @Test
  fun getAgentWorks() = test { client ->
    val response =
      client.get("/admin/agents/${agentOne.id}") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<AgentFull>()
    assertEquals(agentOne.id, body.agent.id)
  }

  @Test
  fun updateAgentWorks() = test { client ->
    val agent = postgres.testAgent(name = "TestAgentUpdate")
    postgres.testAgentConfiguration(agent.id, version = 1)
    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = PropertyUpdate.Value("description"),
        active = true,
        language = PropertyUpdate.Value("english"),
        configuration =
          UpdateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4o-mini",
            temperature = 0.5,
            maxCompletionTokens = PropertyUpdate.Value(100),
            presencePenalty = PropertyUpdate.Value(0.5),
            instructions = null,
          ),
      )

    val response =
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)

    val responseCheck =
      client.get("/admin/agents/${agent.id}") { header(HttpHeaders.Cookie, adminAccessToken()) }

    val body = responseCheck.body<AgentWithConfiguration>()
    assertEquals("TestAgentOneUpdated", body.agent.name)
    assertEquals(0.5, body.configuration.temperature)
    assertEquals(2, body.configuration.version)
    assertEquals(100, body.configuration.maxCompletionTokens)
    assertEquals(0.5, body.configuration.presencePenalty)

    // Leave out presence penalty, to assert it is left unchanged
    // Set maxCompletionTokens to null, to assert it is changed to null
    val propertiesUpdateTest =
      """
      {
        "name": "TestAgentOneUpdated",
        "description": null,
        "active": true,
        "configuration": {
          "context": "context",
          "llmProvider": "azure",
          "model": "gpt-4o-mini",
          "temperature": 0.5,
          "maxCompletionTokens": null
        }
      }
    """
        .trimIndent()

    client.put("/admin/agents/${agent.id}") {
      header(HttpHeaders.Cookie, adminAccessToken())
      contentType(ContentType.Application.Json)
      setBody(propertiesUpdateTest)
    }

    val responseLeaveProperties =
      client.get("/admin/agents/${agent.id}") { header(HttpHeaders.Cookie, adminAccessToken()) }

    val propertiesCheck = responseLeaveProperties.body<AgentWithConfiguration>()

    assertNull(propertiesCheck.agent.description)
    assertEquals("english", propertiesCheck.agent.language)
    assertNull(propertiesCheck.configuration.maxCompletionTokens)
    assertEquals(0.5, propertiesCheck.configuration.presencePenalty)

    postgres.deleteTestAgent(agent.id)
  }

  @Test
  fun updatingMaxCompletionTokensToZeroFails() = test { client ->
    val agent = postgres.testAgent()
    postgres.testAgentConfiguration(agent.id, version = 1)

    val updateAgent =
      UpdateAgent(
        configuration = UpdateAgentConfiguration(maxCompletionTokens = PropertyUpdate.Value(0))
      )

    val response =
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(422, response.status.value)

    postgres.deleteTestAgent(agent.id)
  }

  @Test
  fun updatingAgentInstructionsWorks() = test { client ->
    val agent = postgres.testAgent()
    postgres.testAgentConfiguration(agent.id, version = 1)

    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = PropertyUpdate.Value("description"),
        active = true,
        language = PropertyUpdate.Value("english"),
        configuration =
          UpdateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4o-mini",
            temperature = 0.5,
            instructions =
              UpdateAgentInstructions(
                titleInstruction = PropertyUpdate.Value("title"),
                errorMessage = PropertyUpdate.Value("error"),
              ),
          ),
      )

    val response =
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)

    val body = response.body<AgentWithConfiguration>()

    assertEquals("TestAgentOneUpdated", body.agent.name)
    assertEquals("description", body.agent.description)
    assert(body.agent.active)
    assertEquals("english", body.agent.language)
    assertEquals("context", body.configuration.context)
    assertEquals("azure", body.configuration.llmProvider)
    assertEquals("gpt-4o-mini", body.configuration.model)
    assertEquals(0.5, body.configuration.temperature)
    assertEquals(2, body.configuration.version)
    assertEquals("title", body.configuration.agentInstructions.titleInstruction)
    assertEquals("error", body.configuration.agentInstructions.errorMessage)
  }

  @Test
  fun updatingAgentMetadataDoesNotBumpAgentVersion() = test { client ->
    val agent = postgres.testAgent()
    postgres.testAgentConfiguration(agent.id, version = 1)

    val updateAgent =
      UpdateAgent(
        name = "MyAgent",
        description = PropertyUpdate.Value("description"),
        active = true,
        language = PropertyUpdate.Value("english"),
      )

    val response =
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)

    val body = response.body<AgentWithConfiguration>()

    assertEquals("MyAgent", body.agent.name)
    assertEquals("description", body.agent.description)
    assert(body.agent.active)
    assertEquals("english", body.agent.language)
    assertEquals(1, body.configuration.version)

    postgres.deleteTestAgent(agent.id)
  }

  @Test
  fun updateAgentNameSameConfigurationSameVersionWorks() = test { client ->
    val agent = postgres.testAgent(name = "TestAgentUpdate")
    postgres.testAgentConfiguration(
      agent.id,
      version = 1,
      llmProvider = "openai",
      model = "gpt-4o-mini",
      temperature = 0.1,
      presencePenalty = 0.1,
      maxCompletionTokens = 100,
    )
    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = PropertyUpdate.Value("description"),
        active = true,
        language = PropertyUpdate.Value("english"),
        configuration =
          UpdateAgentConfiguration(
            context = "Test",
            llmProvider = "openai",
            model = "gpt-4o-mini",
            temperature = 0.1,
            presencePenalty = PropertyUpdate.Value(0.1),
            maxCompletionTokens = PropertyUpdate.Value(100),
          ),
      )

    val response =
      client.put("/admin/agents/${agent.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentWithConfiguration>()
    assertEquals("TestAgentOneUpdated", body.agent.name)
    assertEquals(1, body.configuration.version)
    assertEquals(0.1, body.configuration.temperature)

    postgres.deleteTestAgent(agent.id)
  }

  @Test
  fun updateAgentFailsNotFound() = test { client ->
    val updateAgent =
      UpdateAgent(
        name = "TestAgentOneUpdated",
        description = PropertyUpdate.Value("description"),
        active = true,
        language = PropertyUpdate.Value("english"),
        configuration =
          UpdateAgentConfiguration(
            context = "context",
            llmProvider = "azure",
            model = "gpt-4o-mini",
            temperature = 0.5,
            instructions = null,
          ),
      )

    val response =
      client.put("/admin/agents/${KUUID.randomUUID()}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updateAgent)
      }

    assertEquals(404, response.status.value)
    assertEquals("API", response.body<AppError>().errorType)
    assertEquals(ErrorReason.EntityDoesNotExist, response.body<AppError>().errorReason)
  }

  @Test
  fun deleteAgentWorks() = test { client ->
    val response =
      client.delete("/admin/agents/${agentTwo.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun getAgentVersionsWorks() = test { client ->
    val response =
      client.get("/admin/agents/${agentOne.id}/versions?sortOrder=desc") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentConfiguration>>()
    assertEquals(2, body.total)
    assertEquals(2, body.items.size)
    assertEquals(2, body.items[0].version)
    assertEquals(agentOneConfigurationV2.id, body.items[0].id)
    assertEquals(1, body.items[1].version)
    assertEquals(agentOneConfigurationV1.id, body.items[1].id)
  }

  @Test
  fun getAgentVersionWorks() = test { client ->
    val response =
      client.get("/admin/agents/${agentOne.id}/versions/${agentOneConfigurationV1.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentConfigurationWithEvaluationCounts>()
    assertEquals(agentOneConfigurationV1.id, body.configuration.id)
    assertEquals(agentOneConfigurationV1.version, body.configuration.version)
    assertEquals(agentOneConfigurationV1.context, body.configuration.context)
    assertEquals(agentOneConfigurationV1.llmProvider, body.configuration.llmProvider)
    assertEquals(agentOneConfigurationV1.model, body.configuration.model)
    assertEquals(agentOneConfigurationV1.temperature, body.configuration.temperature)
    assertEquals(2, body.evaluationCounts.total)
    assertEquals(1, body.evaluationCounts.positive)
    assertEquals(1, body.evaluationCounts.negative)
  }

  @Test
  fun getAgentVersionEvaluatedMessagesWorks() = test { client ->
    val response =
      client.get("/admin/agents/${agentOne.id}/versions/${agentOneConfigurationV1.id}/messages") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<MessageGroupAggregate>>()
    assertEquals(2, body.total)
    assertEquals(
      chatPositiveMessage.group.id,
      body.items.first { it.evaluation!!.evaluation == true }.group.id,
    )
    assertEquals(
      chatNegativeMessage.group.id,
      body.items.first { it.evaluation!!.evaluation == false }.group.id,
    )
  }

  @Test
  fun rollbackAgentVersionWorks() = test { client ->
    val response =
      client.put("/admin/agents/${agentOne.id}/versions/${agentOneConfigurationV1.id}/rollback") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(200, response.status.value)
    val body = response.body<AgentWithConfiguration>()
    assertEquals(agentOne.id, body.agent.id)
    assertEquals(agentOneConfigurationV1.id, body.agent.activeConfigurationId)
    assertEquals(agentOneConfigurationV1.id, body.configuration.id)
  }

  @Test
  fun listingWithWhatsAppWorks() = test { client ->
    postgres.setWhatsAppAgent(agentOne.id)

    val response = client.get("/admin/agents") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<AgentDisplay>>()
    assertEquals(2, body.total)
    assertTrue(body.items.any { it.whatsapp })
  }
}
