package net.barrage.llmao.core.services

import kotlinx.coroutines.runBlocking
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_PROMPT
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.workflow.chat.ChatAgent
import net.barrage.llmao.app.workflow.chat.toChatAgent
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.DEFAULT_TITLE_INSTRUCTION
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.tokens.TokenUsageTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatAgentTests : IntegrationTest(useWiremock = true) {
  private lateinit var chatAgent: ChatAgent
  private lateinit var admin: User
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var chat: Chat

  @BeforeAll
  fun setup() {
    runBlocking {
      admin = postgres.testUser(admin = true)
      agent = postgres.testAgent()
      agentConfiguration =
        postgres.testAgentConfiguration(
          agent.id,
          llmProvider = "openai",
          model = "gpt-4o",
          titleInstruction = COMPLETIONS_TITLE_PROMPT,
          summaryInstruction = "Custom summary instruction",
        )
      chat = postgres.testChat(admin.id, agent.id, null)
      chatAgent =
        AgentFull(agent, configuration = agentConfiguration, collections = listOf())
          .toChatAgent(
            history = MessageBasedHistory(),
            providers = app.providers,
            settings = app.settingsService.getAllWithDefaults(),
            tokenTracker =
              TokenUsageTracker(
                userId = admin.id,
                agentId = agent.id,
                agentConfigurationId = agentConfiguration.id,
                origin = "test",
                originId = chat.id,
                repository = app.repository.tokenUsageW,
              ),
            toolchain = null,
          )
    }
  }

  @Test
  fun successfullyGeneratesChatTitle() = test {
    // Title responses are always the same regardless of the prompt
    val response = chatAgent.createTitle("Test prompt - title", "Test response - title")
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response.content)
  }

  @Test
  fun successfullyCompletesChat() = test {
    // To trigger direct responses, the following prompt has to be somewhere the message
    val response =
      chatAgent.chatCompletionWithRag(listOf(ChatMessage.user(COMPLETIONS_COMPLETION_PROMPT)))
    assertEquals(COMPLETIONS_RESPONSE, response.content)
  }

  @Test
  fun createTitleCustom() = test {
    val titleInstruction = agentConfiguration.agentInstructions.titleInstruction()
    assertEquals(COMPLETIONS_TITLE_PROMPT, titleInstruction)
  }

  @Test
  fun createTitleDefault() = test {
    val defaultAgentConfiguration =
      postgres.testAgentConfiguration(agent.id, llmProvider = "openai", model = "gpt-4o")

    val titleInstruction = defaultAgentConfiguration.agentInstructions.titleInstruction()
    assertEquals(DEFAULT_TITLE_INSTRUCTION.trimMargin(), titleInstruction)
  }
}
