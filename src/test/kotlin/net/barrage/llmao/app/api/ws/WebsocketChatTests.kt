package net.barrage.llmao.app.api.ws

import com.aallam.openai.api.core.FinishReason
import io.ktor.websocket.*
import kotlin.random.Random
import kotlinx.serialization.SerializationException
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE_PROMPT
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.chatSession
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.json
import net.barrage.llmao.openNewChat
import net.barrage.llmao.sendMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        embeddingProvider = "openai",
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
        embeddingProvider = "openai",
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
   * Beforehand, the agent is configured to the right collection.
   */
  @Test
  fun worksWhenAgentIsConfiguredProperly() = wsTest { client ->
    client.chatSession(session.sessionId) {
      openNewChat(validAgent.id)

      var asserted = false
      var buffer = ""

      sendMessage("Will this trigger a stream response?") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          try {
            val finishEvent = json.decodeFromString<ServerMessage>(response)

            // React only to finish events since titles can also get sent
            if (finishEvent is ServerMessage.FinishEvent) {
              assert(finishEvent.reason == FinishReason.Stop)
              assertNull(finishEvent.content)
              asserted = true
              break
            }
          } catch (e: SerializationException) {
            val errMessage = e.message ?: throw e
            if (!errMessage.startsWith("Expected JsonObject, but had JsonLiteral")) {
              throw e
            }
            buffer += response
          } catch (e: Throwable) {
            e.printStackTrace()
            break
          }
        }
      }

      assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)
      assert(asserted)
    }
  }

  /**
   * A client connects via Websocket, opens a chat, sends a message and receives an error.
   * Beforehand, the agent is configured to the wrong collection (whose size is different than the
   * embeddings created by the agent's model).
   */
  @Test
  fun sendsVectorDatabaseErrorWhenAgentEmbeddingModelIsNotConfiguredProperly() = wsTest { client ->
    var asserted = false
    client.chatSession(session.sessionId) {
      openNewChat(invalidAgent.id)
      sendMessage("Will this trigger a stream response?") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          val error = json.decodeFromString<AppError>(response)
          assertEquals(ErrorReason.VectorDatabase, error.reason)
          assertTrue(error.description!!.contains("vector lengths don't match"))
          asserted = true
          break
        }
      }
    }
    assert(asserted)
  }

  private fun insertTestVectors() {
    val streamResponsePrompt =
      Pair(COMPLETIONS_STREAM_RESPONSE_PROMPT, List(SIZE) { Random.nextFloat() })

    weaviate!!.insertVectors(TEST_COLLECTION, listOf(streamResponsePrompt))
  }
}
