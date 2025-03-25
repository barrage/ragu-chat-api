package net.barrage.llmao.app.api.ws

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.adminWsSession
import net.barrage.llmao.app.chat.ChatWorkflowMessage
import net.barrage.llmao.app.workflow.IncomingMessageSerializer
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.UpdateAgent
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.json
import net.barrage.llmao.openNewChat
import net.barrage.llmao.sendClientSystem
import net.barrage.llmao.sendMessage
import net.barrage.llmao.userWsSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val TEST_COLLECTION = "KusturicaChatTests"

private const val SIZE = 1536

class WebsocketChatWorkflowTests : IntegrationTest(useWeaviate = true) {
  @BeforeAll
  fun setup() {
    runBlocking { weaviate!!.insertTestCollection(TEST_COLLECTION, SIZE) }
  }

  @Test
  fun collectionIsSkippedIfUserDoesNotBelongToItsGroups() = wsTest { client ->
    val collectionName = "KusturicaChatUserTest"
    weaviate!!.insertTestCollection(collectionName, SIZE, groups = listOf("admin"))

    val streamResponsePrompt = Pair(COMPLETIONS_STREAM_PROMPT, List(SIZE) { Random.nextFloat() })
    weaviate!!.insertVectors(collectionName, listOf(streamResponsePrompt))

    val agent = createValidAgent(collection = collectionName)

    var asserted = false

    client.userWsSession {
      openNewChat(agent.agent.id)

      var buffer = ""

      // Wiremock is configured to return an empty stream for this prompt
      sendMessage("INSUFFICIENT_PERMISSIONS_TEST") { incoming ->
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

      assert(buffer.isBlank())
    }

    assert(asserted)
  }

  /**
   * Tests if the whole application flow works as expected.
   *
   * A client connects via Websocket, opens a chat, sends a message and receives a response.
   * Beforehand, the agent is configured to the right collection.
   *
   * This also tests whether the right collection was queried, as the contents from it will have the
   * necessary contents to trigger the right wiremock response.
   */
  @Test
  fun worksWhenAgentIsConfiguredProperly() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val validAgent = createValidAgent()

    client.adminWsSession {
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

    client.adminWsSession {
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

  @Test
  fun storesChatOnlyAfterFirstMessagePair() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val validAgent = createValidAgent()

    client.adminWsSession {
      val chatId = openNewChat(validAgent.agent.id)

      val error = assertThrows<AppError> { app.services.chat.getChat(chatId, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, error.errorReason)

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
      assertEquals(2, chat.messages.items[0].messages.size)
    }

    deleteVectors()

    assert(asserted)
  }

  @Test
  fun doesNotStoreChatBeforeFirstMessagePair() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val validAgent = createValidAgent()

    client.adminWsSession {
      val chatOne = openNewChat(validAgent.agent.id)
      val errorOne =
        assertThrows<AppError> { app.services.chat.getChat(chatOne, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, errorOne.errorReason)

      val chatTwo = openNewChat(validAgent.agent.id)
      val errorTwo =
        assertThrows<AppError> { app.services.chat.getChat(chatTwo, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, errorTwo.errorReason)

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

    client.adminWsSession {
      val chatId = openNewChat(validAgent.agent.id)

      val error = assertThrows<AppError> { app.services.chat.getChat(chatId, Pagination(1, 50)) }
      assertEquals(ErrorReason.EntityDoesNotExist, error.errorReason)

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
      assertEquals(2, chat.messages.items[0].messages.size)
    }

    deleteVectors()

    assert(asserted)
  }

  @Test
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
        client1.adminWsSession {
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

      delay(1000)

      var buffer = ""
      client2.adminWsSession {
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

    client.adminWsSession {
      openNewChat(agent.agent.id)
      var buffer = ""
      sendMessage("Will this trigger a stream response?") { incoming ->
        // Block on this so we can see what happens.
        runBlocking {
          httpClient.put("/admin/agents/${agent.agent.id}") {
            header(HttpHeaders.Cookie, adminAccessToken())
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

    deleteVectors()

    assert(asserted)
  }

  @Test
  fun sendsAgentErrorMessageWhenErrorIsEncountered() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_ERROR_PROMPT)

    val validAgent = createValidAgent("This is an error message.")

    client.adminWsSession {
      openNewChat(validAgent.agent.id)

      sendMessage("Give me an error") { incoming ->
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

  @Test
  fun properlyLoadsExistingMessages() = wsTest { client ->
    var asserted = false

    insertVectors(COMPLETIONS_STREAM_PROMPT)

    val validAgent = createValidAgent()

    val prompt = "Will this trigger a stream response?"

    client.adminWsSession {
      val chatId = openNewChat(validAgent.agent.id)

      var buffer = ""

      sendMessage("0: $prompt") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
            buffer += message.chunk
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
            assert(message.reason == FinishReason.Stop)
            break
          } catch (_: SerializationException) {}
        }
      }

      assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)
      buffer = ""

      sendMessage("1: $prompt") { incoming ->
        for (frame in incoming) {
          val response = (frame as Frame.Text).readText()
          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamChunk>(response)
            buffer += message.chunk
          } catch (_: SerializationException) {}

          try {
            val message = json.decodeFromString<ChatWorkflowMessage.StreamComplete>(response)
            assert(message.reason == FinishReason.Stop)
            break
          } catch (_: SerializationException) {}
        }
      }

      assertEquals(COMPLETIONS_STREAM_RESPONSE, buffer)

      val chat = app.services.chat.getChat(chatId, Pagination(1, 50))

      // These are the two message groups
      assertEquals(2, chat.messages.items.size)

      val messages = chat.messages.items.flatMap { it.messages }

      // Latest messages are the first one in the list

      assertEquals("1: $prompt", messages[0].content)
      assertEquals(COMPLETIONS_STREAM_RESPONSE, messages[1].content)
      assertEquals("0: $prompt", messages[2].content)
      assertEquals(COMPLETIONS_STREAM_RESPONSE, messages[3].content)

      asserted = true
    }

    deleteVectors()

    assert(asserted)
  }

  // Valid and invalid here refer to configuration, not the actual models and objects.

  private suspend fun createValidAgent(
    errorMessage: String? = null,
    collection: String = TEST_COLLECTION,
  ): AgentFull {
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
        collection = collection,
        amount = 2,
        instruction = "Use the valuable information below to solve the three body problem.",
        embeddingProvider = "openai",
        embeddingModel = "text-embedding-ada-002", // 1536
        vectorProvider = "weaviate",
      )

    return AgentFull(validAgent, validAgentConfiguration, listOf(validAgentCollection))
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
