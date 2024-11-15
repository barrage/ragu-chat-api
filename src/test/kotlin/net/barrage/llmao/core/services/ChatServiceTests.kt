package net.barrage.llmao.core.services

import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatServiceTests : IntegrationTest(useWiremockOpenAi = true) {
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
    val title = chatService.generateTitle(chat.id, "Test prompt", agent.id)
    assertEquals("v1_chat_completions_response", title)
  }
}
