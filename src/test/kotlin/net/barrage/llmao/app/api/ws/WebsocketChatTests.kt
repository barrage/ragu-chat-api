package net.barrage.llmao.app.api.ws

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlin.random.Random
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
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

/** Prompt configured to make wiremock return a stream response */
private const val TEST_STREAM_PROMPT = "v1_chat_completions_stream"

private const val SIZE = 1536

class WebsocketChatTests :
  IntegrationTest(
    useWiremock = true,
    useWeaviate = true,
    // wiremockUrlOverride = "http://localhost:8080",
  ) {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var user: User
  private lateinit var session: Session
  private lateinit var agentCollection: AgentCollection

  @BeforeAll
  fun setup() {
    agent =
      postgres!!.testAgent(
        embeddingProvider = "azure",
        embeddingModel = "text-embedding-ada-002", // 1536
      )
    agentConfiguration =
      postgres!!.testAgentConfiguration(
        agentId = agent.id,
        llmProvider = "openai",
        model = "gpt-4o",
      )
    user = postgres!!.testUser(email = "not@important.org", admin = false)
    session = postgres!!.testSession(user.id)
    agentCollection =
      postgres!!.testAgentCollection(agent.id, TEST_COLLECTION, 2, "You pass the butter")
    insertTestVectors()
  }

  /**
   * Tests if the whole application flow works as expected.
   *
   * A client connects via Websocket, opens a chat, sends a message and receives a response.
   */
  @Test
  fun given_ValidAgentConfiguration_when_UserSendsMessage_then_AgentResponds() = wsTest { client ->
    client.chatSession(session.sessionId) {
      openNewChat(agent.id)
      val response = sendMessage(TEST_STREAM_PROMPT)
      assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
    }
  }

  private fun insertTestVectors() {
    val truthOne = Pair("Pee is stored in the balls.", List(SIZE) { Random.nextFloat() })
    val truthTwo = Pair("It's flat, nice try globeheads.", List(SIZE) { Random.nextFloat() })

    weaviate!!.insertVectors(TEST_COLLECTION, listOf(truthOne, truthTwo))
  }
}
