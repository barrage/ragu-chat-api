package net.barrage.llmao.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.ADMIN_USER
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_PROMPT
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.chat.ChatAgent
import net.barrage.llmao.app.chat.toChatAgent
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TokenUsageTests : IntegrationTest() {
  private lateinit var workflow: ChatAgent
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
      val completionParameters =
        ChatCompletionParameters(
          model = "gpt-4o",
          temperature = agentConfiguration.temperature,
          presencePenalty = agentConfiguration.presencePenalty ?: 0.0,
          maxTokens = agentConfiguration.maxCompletionTokens,
          tools = null,
        )
      chat = postgres.testChat(ADMIN_USER, agent.id, null)
      workflow =
        AgentFull(agent, configuration = agentConfiguration, collections = listOf())
          .toChatAgent(
            history = MessageBasedHistory(),
            providers = app.providers,
            settings = app.services.settings.getAllWithDefaults(),
            tokenTracker =
              TokenUsageTracker(
                userId = ADMIN_USER.id,
                username = ADMIN_USER.username,
                agentId = agent.id,
                agentConfigurationId = agentConfiguration.id,
                origin = "test",
                originId = chat.id,
                repository = app.repository.tokenUsageW,
              ),
            toolchain = null,
            completionParameters = completionParameters,
          )
    }
  }

  @Test
  fun registersUsageWhenCallingChatCompletion() = test {
    val buffer = mutableListOf(ChatMessage.user(COMPLETIONS_COMPLETION_PROMPT))
    workflow.chatCompletion(buffer)
    assertEquals(COMPLETIONS_RESPONSE, buffer.last().content)

    // Use delays since storing the usage is done in a separate coroutine
    delay(200)
    val usage =
      app.services.admin.listTokenUsage(agentId = agent.id).items.find {
        it.usageType == TokenUsageType.COMPLETION
      }!!
    assertEquals("test", usage.origin.type)
    assertEquals(chat.id, usage.origin.id)
    assertEquals(ADMIN_USER.id, usage.userId)
    assertEquals(agent.id, usage.agentId)
  }

  @Test
  fun registersUsageWhenCallingTitleCompletion() = test {
    val response = workflow.createTitle("foo", "bar")
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response.content)

    // Use delays since storing the usage is done in a separate coroutine
    delay(200)
    val usage =
      app.services.admin.listTokenUsage(agentId = agent.id).items.find {
        it.usageType == TokenUsageType.COMPLETION_TITLE
      }!!
    assertEquals("test", usage.origin.type)
    assertEquals(chat.id, usage.origin.id)
    assertEquals(ADMIN_USER.id, usage.userId)
    assertEquals(agent.id, usage.agentId)
  }
}
