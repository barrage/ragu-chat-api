package net.barrage.llmao.core.services

import kotlinx.coroutines.flow.toList
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ConversationServiceTests : IntegrationTest(useWiremock = true) {
  private lateinit var service: ConversationService

  private lateinit var admin: User
  private lateinit var agent: Agent
  private lateinit var chat: Chat

  @BeforeAll
  fun setup() {
    service = app.services.conversation

    admin = postgres.testUser(admin = true)
    agent = postgres.testAgent()
    postgres.testAgentConfiguration(agent.id, llmProvider = "openai", model = "gpt-4o")
    chat = postgres.testChat(admin.id, agent.id, null)
  }

  @Test
  fun successfullyGeneratesChatTitle() = test {
    // Title responses are always the same regardless of the prompt
    val response = service.createAndUpdateTitle(chat.id, "Test prompt - title", agent.id)
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response)
  }

  @Test
  fun successfullyStreamsChat() = test {
    // To trigger streams, the following prompt has to be somewhere the message
    val stream = service.chatCompletionStream(COMPLETIONS_STREAM_PROMPT, listOf(), agent.id)
    val response =
      stream.toList().joinToString("") { chunk -> chunk.joinToString { it.content ?: "" } }
    assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
  }

  @Test
  fun successfullyCompletesChat() = test {
    // To trigger direct responses, the following prompt has to be somewhere the message
    val response = service.chatCompletion(COMPLETIONS_COMPLETION_PROMPT, listOf(), agent.id)
    assertEquals(COMPLETIONS_RESPONSE, response)
  }
}
