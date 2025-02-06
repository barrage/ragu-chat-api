package net.barrage.llmao.app.api.ws

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import net.barrage.llmao.COMPLETIONS_ERROR_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_LONG_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_WHITESPACE_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_WHITESPACE_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_PROMPT
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.workflow.IncomingMessageSerializer
import net.barrage.llmao.app.workflow.chat.ChatWorkflowMessage
import net.barrage.llmao.chatSession
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.json
import net.barrage.llmao.openNewChat
import net.barrage.llmao.sendClientSystem
import net.barrage.llmao.sendMessage
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val TEST_COLLECTION = "KusturicaChatTests"

private const val SIZE = 1536

class WebsocketChatWorkflowTests : IntegrationTest(useWiremock = true, useWeaviate = true) {
  private lateinit var user: User
  private lateinit var session: Session

  @BeforeAll
  fun setup() {
    runBlocking {
      user = postgres.testUser(email = "not@important.org", admin = false)
      session = postgres.testSession(user.id)
    }
  }

  /**
   * Tests if the whole application flow works as expected.
   *
   * A client connects via Websocket, opens a chat, sends a message and receives a response.
   * Beforehand, the agent is configured to the right collection.
   *
   * This also tests whether the right collection was queried, as the contents from it will have the
   * necessary contents to trigger the right prompt.
   */
  @Test
  fun worksWhenAgentIsConfiguredProperly() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val validAgent = createValidAgent()

    client.chatSession(session.sessionId) {
      openNewChat(validAgent.agent.id)

      var buffer = ""

      sendMessage("Will this trigger a stream response?") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
            buffer += message.chunk
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
            assert(message.reason == FinishReason.Stop)
            asserted = true
            break
          } catch (_: SerializationException) {}
        }
      }

      assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)
    }

    deleteVectors()

    assert(asserted)
  }

  /**
   * Tests for properly formatted whitespace messages in cases where LLMs send a single space or
   * newline as a token.
   */
  @Test
  fun properlySendsWhitespaceMessages() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_WHITESPACE_PROMPT)

    val validAgent = createValidAgent()

    client.chatSession(session.sessionId) {
      openNewChat(validAgent.agent.id)

      var buffer = ""

      sendMessage("Stream me whitespace") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
            buffer += message.chunk
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
            assert(message.reason == FinishReason.Stop)
            asserted = true
            break
          } catch (_: SerializationException) {}
        }

        assertEquals(COMPLETIONS_STREAM_WHITESPACE_RESPONSE, buffer)
      }
    }

    deleteVectors()

    assert(asserted)
  }

  /**
   * A client connects via Websocket, opens a chat, sends a message and receives an error.
   * Beforehand, the agent is configured to the wrong collection (whose embedding size is different
   * than the embeddings created by the agent's model). Theoretically should never happen since now
   * we load embedding configuration from the collection itself, but still nice to have as a test
   * scenario.
   */
  @Test
  fun sendsVectorDatabaseErrorWhenAgentEmbeddingModelIsNotConfiguredProperly() = wsTest { client ->
    var asserted = false

    // We need to insert vectors here because Weaviate will not throw any errors
    // if the collection does not contain vectors.
    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val invalidAgent = createInvalidAgent()

    client.chatSession(session.sessionId) {
      openNewChat(invalidAgent.agent.id)

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

    deleteVectors()

    assert(asserted)
  }

  @Test
  fun storesChatOnlyAfterFirstMessagePair() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val validAgent = createValidAgent()

    client.chatSession(session.sessionId) {
      val chatId = openNewChat(validAgent.agent.id)

      val error = assertThrows<AppError> { app.services.chat.getChat(chatId, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, error.reason)

      var buffer = ""

      sendMessage("Will this trigger a stream response?") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
            buffer += message.chunk
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
            assert(message.reason == FinishReason.Stop)
            asserted = true
            break
          } catch (_: SerializationException) {}
        }
      }

      assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)

      val chat = app.services.chat.getChat(chatId, Pagination(1, 50))
      assertEquals(2, chat.messages.size)
    }

    deleteVectors()

    assert(asserted)
  }

  @Test
  fun doesNotStoreChatBeforeFirstMessagePair() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val validAgent = createValidAgent()

    client.chatSession(session.sessionId) {
      val chatOne = openNewChat(validAgent.agent.id)
      val errorOne =
        assertThrows<AppError> { app.services.chat.getChat(chatOne, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, errorOne.reason)

      val chatTwo = openNewChat(validAgent.agent.id)
      val errorTwo =
        assertThrows<AppError> { app.services.chat.getChat(chatTwo, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, errorTwo.reason)

      asserted = true
    }

    deleteVectors()

    assert(asserted)
  }

  @Test
  fun storesChatAfterIncompleteFirstMessagePair() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_LONG_PROMPT)

    val validAgent = createValidAgent()

    client.chatSession(session.sessionId) {
      val chatId = openNewChat(validAgent.agent.id)

      val error = assertThrows<AppError> { app.services.chat.getChat(chatId, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, error.reason)

      val msg = "{ \"type\": \"chat\", \"text\": \"Will this trigger a stream response?\" }"

      send(Frame.Text(msg))

      var cancelSent = false

      for (frame in incoming) {
        val response = (frame as Frame.Text).readText()
        try {
          json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
          // Send cancel immediately after first chunk
          if (!cancelSent) {
            cancelSent = true
            sendClientSystem(IncomingSystemMessage.CancelWorkflowStream)
          }
        } catch (_: SerializationException) {}

        try {
          val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
          assert(message.reason == FinishReason.ManualStop)
          asserted = true
          break
        } catch (_: SerializationException) {}
      }

      val chat = app.services.chat.getChat(chatId, Pagination(1, 50))
      assertEquals(2, chat.messages.size)
    }

    deleteVectors()

    assert(asserted)
  }

  @RepeatedTest(10)
  fun sameUserCanOpenMultipleChatsAtOnce() = test {
    var assertedFirst = false
    var assertedSecond = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val client1 = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(IncomingMessageSerializer)
      }
    }

    val client2 = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(IncomingMessageSerializer)
      }
    }

    val validAgent = createValidAgent()

    runBlocking {
      launch {
        var buffer = ""
        client1.chatSession(session.sessionId) {
          openNewChat(validAgent.agent.id)
          // Wait a bit before actually sending the message
          delay(500)
          sendMessage("Will this trigger a stream response?") { incoming ->
            for (frame in incoming) {
              val response = (frame as Frame.Text).readText()
              try {
                val message = json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
                buffer += message.chunk
              } catch (_: SerializationException) {}
              try {
                val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
                assert(message.reason == FinishReason.Stop)
                assertedFirst = true
                break
              } catch (_: SerializationException) {}
            }

            assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)

            sendClientSystem(IncomingSystemMessage.CloseWorkflow)
          }
        }
      }

      delay(500)

      var buffer = ""
      client2.chatSession(session.sessionId) {
        openNewChat(validAgent.agent.id)
        sendMessage("Will this trigger a stream response?") { incoming ->
          for (frame in incoming) {
            val response = (frame as Frame.Text).readText()
            try {
              val message = json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
              buffer += message.chunk
            } catch (_: SerializationException) {}
            try {
              val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
              assert(message.reason == FinishReason.Stop)
              assertedSecond = true
              break
            } catch (_: SerializationException) {}
          }
        }

        assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)

        sendClientSystem(IncomingSystemMessage.CloseWorkflow)
      }
    }

    deleteVectors()

    assert(assertedFirst)
    assert(assertedSecond)
  }

  @Test
  fun removesChatWhenAgentIsDeactivated() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val httpClient = createClient { install(ContentNegotiation) { json() } }
    val agent = createValidAgent()

    // Necessary for the agent update route
    val admin = postgres.testUser(email = "chad@eternal.grind", admin = true)
    val adminSession = postgres.testSession(admin.id)

    client.chatSession(session.sessionId) {
      openNewChat(agent.agent.id)
      var buffer = ""
      sendMessage("Will this trigger a stream response?") { incoming ->
        // Block on this so we can see what happens.
        runBlocking {
          httpClient.put("/admin/agents/${agent.agent.id}") {
            header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
            contentType(ContentType.Application.Json)
            setBody(UpdateAgent(active = false))
          }
        }

        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          try {
            val event = json.decodeFromString<ChatWorkflowMessage>(response)

            if (event is ChatWorkflowMessage.StreamChunk) {
              buffer += event.chunk
            }

            if (event is ChatWorkflowMessage.StreamComplete) {
              break
            }
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<OutgoingSystemMessage.AgentDeactivated>(response)
            // Asserts the right agent was deactivated
            assert(message.agentId == agent.agent.id)
            asserted = true
          } catch (_: SerializationException) {}
        }
      }

      // Asserts the stream was completed successfully
      assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)
    }

    assert(asserted)
  }

  @Test
  fun sendsAgentErrorMessageWhenErrorIsEncountered() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_ERROR_PROMPT)

    val validAgent = createValidAgent("This is an error message.")

    client.chatSession(session.sessionId) {
      openNewChat(validAgent.agent.id)

      sendMessage("Will this trigger a stream response?") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          val message = json.decodeFromString<AppError>(response)
          assertEquals("This is an error message.", message.displayMessage)
          asserted = true
          break
        }
      }
    }

    deleteVectors()

    assert(asserted)
  }

  // Valid and invalid here refer to configuration, not the actual models and objects.

  private suspend fun createValidAgent(errorMessage: String? = null): AgentFull {
    val validAgent = postgres.testAgent()

    val validAgentConfiguration =
      postgres.testAgentConfiguration(
        agentId = validAgent.id,
        llmProvider = "openai",
        model = "gpt-4o",
        titleInstruction = COMPLETIONS_TITLE_PROMPT,
        errorMessage = errorMessage,
      )

    val validAgentCollection =
      postgres.testAgentCollection(
        agentId = validAgent.id,
        collection = TEST_COLLECTION,
        amount = 2,
        instruction = "Use the valuable information below to solve the three body problem.",
        embeddingProvider = "openai",
        embeddingModel = "text-embedding-ada-002", // 1536
        vectorProvider = "weaviate",
      )

    return AgentFull(validAgent, validAgentConfiguration, listOf(validAgentCollection))
  }

  private suspend fun createInvalidAgent(): AgentFull {
    val invalidAgent = postgres.testAgent()

    val invalidAgentConfiguration =
      postgres.testAgentConfiguration(
        agentId = invalidAgent.id,
        llmProvider = "openai",
        model = "gpt-4o",
      )

    val invalidAgentCollection =
      postgres.testAgentCollection(
        invalidAgent.id,
        TEST_COLLECTION,
        2,
        "Use the valuable information below to pass the butter.",
        embeddingProvider = "openai",
        embeddingModel = "text-embedding-3-large", // 3072
        vectorProvider = "weaviate",
      )

    return AgentFull(invalidAgent, invalidAgentConfiguration, listOf(invalidAgentCollection))
  }

  private fun insertVectors(content: String) {
    // Contains the necessary string to trigger the stream response from Wiremock.
    val streamResponsePrompt = Pair(content, List(SIZE) { Random.nextFloat() })
    weaviate!!.insertVectors(TEST_COLLECTION, listOf(streamResponsePrompt))
  }

  private fun deleteVectors() {
    weaviate!!.deleteVectors(TEST_COLLECTION)
  }
}
