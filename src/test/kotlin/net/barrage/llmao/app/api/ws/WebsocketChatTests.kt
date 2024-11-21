package net.barrage.llmao.app.api.ws

import kotlin.random.Random
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE_PROMPT
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.chatSession
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.openNewChat
import net.barrage.llmao.sendMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

private const val TEST_COLLECTION = "KusturicaChatTests"

private const val SIZE = 1536

class WebsocketChatTests :
  IntegrationTest(
    useWiremock = true,
    useWeaviate = true,
    // wiremockUrlOverride = "http://localhost:8080",
  ) {
  private lateinit var user: User
  private lateinit var session: Session

  private lateinit var validAgent: Agent
  private lateinit var validAgentConfiguration: AgentConfiguration
  private lateinit var validAgentCollection: AgentCollection

  private lateinit var invalidAgent: Agent
  private lateinit var invalidAgentConfiguration: AgentConfiguration
  private lateinit var invalidAgentCollection: AgentCollection

  @BeforeAll
  fun setup() {
    user = postgres!!.testUser(email = "not@important.org", admin = false)
    session = postgres!!.testSession(user.id)

    validAgent =
      postgres!!.testAgent(
        embeddingProvider = "azure",
        embeddingModel = "text-embedding-ada-002", // 1536
      )
    validAgentConfiguration =
      postgres!!.testAgentConfiguration(
        agentId = validAgent.id,
        llmProvider = "openai",
        model = "gpt-4o",
      )
    validAgentCollection =
      postgres!!.testAgentCollection(
        agentId = validAgent.id,
        collection = TEST_COLLECTION,
        amount = 2,
        instruction = "Use the valuable information below to solve the three body problem.",
      )

    invalidAgent =
      postgres!!.testAgent(
        embeddingProvider = "azure",
        embeddingModel = "text-embedding-3-large", // 3072
      )
    invalidAgentConfiguration =
      postgres!!.testAgentConfiguration(
        agentId = invalidAgent.id,
        llmProvider = "openai",
        model = "gpt-4o",
      )
    invalidAgentCollection =
      postgres!!.testAgentCollection(
        invalidAgent.id,
        TEST_COLLECTION,
        2,
        "Use the valuable information below to pass the butter.",
      )
    insertTestVectors()
  }

  /**
   * Tests if the whole application flow works as expected.
   *
   * A client connects via Websocket, opens a chat, sends a message and receives a response.
   */
  @Test
  fun given_ValidAgentConfiguration_when_UserSendsMessage_then_AgentRespondsWithInfoFromCollection() =
    wsTest { client ->
      client.chatSession(session.sessionId) {
        openNewChat(validAgent.id)
        val response = sendMessage("Will this trigger a stream response?")
        assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
      }
    }

  //  @Test
  //  fun given_invalidAgentConfiguration_when_UserSendsMessage_then_ConfigurationErrorIsThrown() =
  //    wsTest { client ->
  //      client.chatSession(session.sessionId) {
  //        openNewChat(invalidAgent.id)
  //        val response = sendMessage("Will this trigger a stream response?")
  //        assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
  //      }
  //    }
  //
  private fun insertTestVectors() {
    val streamResponsePrompt =
      Pair(COMPLETIONS_STREAM_RESPONSE_PROMPT, List(SIZE) { Random.nextFloat() })

    weaviate!!.insertVectors(TEST_COLLECTION, listOf(streamResponsePrompt))
  }
}
