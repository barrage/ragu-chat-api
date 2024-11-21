package net.barrage.llmao.core.services

import kotlinx.coroutines.flow.toList
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatServiceTests : IntegrationTest(useWiremock = true) {
  private lateinit var chatService: ChatService

  private lateinit var admin: User
  private lateinit var agent: Agent
  private lateinit var chat: Chat

  @BeforeAll
  fun setup() {
    chatService = services!!.chat

    admin = postgres!!.testUser(admin = true)
    agent = postgres!!.testAgent()
    postgres!!.testAgentConfiguration(agent.id, llmProvider = "openai", model = "gpt-4o")
    chat = postgres!!.testChat(admin.id, agent.id, null)
  }

  @Test
  fun successfullyGeneratesChatTitle() = test {
    val response = chatService.generateTitle(chat.id, "Test prompt - title", agent.id)
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response)
  }

  @Test
  fun successfullyStreamsChat() = test {
    val stream = chatService.chatCompletionStream("v1_chat_completions_stream", listOf(), agent.id)
    val response =
      stream.toList().joinToString("") { chunk -> chunk.joinToString { it.content ?: "" } }
    assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
  }

  @Test
  fun successfullyCompletesChat() = test {
    val response = chatService.chatCompletion("Test prompt - completion", listOf(), agent.id)
    assertEquals(COMPLETIONS_RESPONSE, response)
  }
}
