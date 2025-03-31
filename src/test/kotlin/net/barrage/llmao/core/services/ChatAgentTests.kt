package net.barrage.llmao.core.services

import kotlinx.coroutines.runBlocking
import net.barrage.llmao.ADMIN_USER
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_PROMPT
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.chat.ChatAgent
import net.barrage.llmao.core.chat.toChatAgent
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.chat.MessageBasedHistory
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.DEFAULT_TITLE_INSTRUCTION
import net.barrage.llmao.core.token.TokenUsageTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatAgentTests : IntegrationTest() {
  private lateinit var chatAgent: ChatAgent
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var chat: Chat

  @BeforeAll
  fun setup() {
    runBlocking {
      agent = postgres.testAgent()
      agentConfiguration =
        postgres.testAgentConfiguration(
          agent.id,
          llmProvider = "openai",
          model = "gpt-4o",
          titleInstruction = COMPLETIONS_TITLE_PROMPT,
        )
      val chatCompletionParameters =
        ChatCompletionParameters(
          model = "gpt-4o",
          temperature = agentConfiguration.temperature,
          presencePenalty = agentConfiguration.presencePenalty ?: 0.0,
          maxTokens = agentConfiguration.maxCompletionTokens,
          tools = null,
        )
      chat = postgres.testChat(ADMIN_USER, agent.id, null)
      chatAgent =
        AgentFull(
            agent,
            configuration = agentConfiguration,
            collections = listOf(),
            groups = listOf(),
          )
          .toChatAgent(
            history = MessageBasedHistory(),
            providers = app.providers,
            settings = app.services.settings.getAllWithDefaults(),
            tokenTracker =
              TokenUsageTracker(
                userId = ADMIN_USER.id,
                agentId = agent.id,
                agentConfigurationId = agentConfiguration.id,
                origin = "test",
                originId = chat.id,
                repository = app.repository.tokenUsageW,
                username = ADMIN_USER.username,
              ),
            toolchain = null,
            completionParameters = chatCompletionParameters,
            allowedGroups = ADMIN_USER.entitlements,
            userId = ADMIN_USER.id,
          )
    }
  }

  @Test
  fun successfullyGeneratesChatTitle() = test {
    // Title responses are always the same regardless of the prompt
    val response = chatAgent.createTitle("Test prompt - title", "Test response - title")
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response)
  }

  @Test
  fun successfullyCompletesChat() = test {
    // To trigger direct responses, the following prompt has to be somewhere the message

    val response = chatAgent.completion(COMPLETIONS_COMPLETION_PROMPT)
    assertEquals(COMPLETIONS_RESPONSE, response.last().content!!.text())
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
