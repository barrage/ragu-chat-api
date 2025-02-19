package net.barrage.llmao.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_SUMMARY_PROMPT
import net.barrage.llmao.COMPLETIONS_SUMMARY_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_PROMPT
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.workflow.chat.ChatAgent
import net.barrage.llmao.app.workflow.chat.toChatAgent
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.tokens.TokenUsageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TokenUsageTests : IntegrationTest(useWiremock = true) {
  private lateinit var workflow: ChatAgent
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
          summaryInstruction = COMPLETIONS_SUMMARY_PROMPT,
        )
      chat = postgres.testChat(admin.id, agent.id, null)
      workflow =
        AgentFull(agent, configuration = agentConfiguration, collections = listOf())
          .toChatAgent(
            providers = app.providers,
            tools = null,
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
          )
    }
  }

  @Test
  fun registersUsageWhenCallingChatCompletion() = test {
    val response =
      workflow.chatCompletionWithRag(listOf(ChatMessage.user(COMPLETIONS_COMPLETION_PROMPT)))
    assertEquals(COMPLETIONS_RESPONSE, response.content)

    // Use delays since storing the usage is done in a separate coroutine
    delay(200)
    val usage =
      app.services.admin.listTokenUsage(agentId = agent.id).items.find {
        it.usageType == TokenUsageType.COMPLETION
      }!!
    assertEquals("test", usage.origin.type)
    assertEquals(chat.id, usage.origin.id)
    assertEquals(admin.id, usage.userId)
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
    assertEquals(admin.id, usage.userId)
    assertEquals(agent.id, usage.agentId)
  }

  @Test
  fun registersUsageWhenCallingSummaryCompletion() = test {
    val response =
      workflow.summarizeConversation(listOf(ChatMessage.user(COMPLETIONS_SUMMARY_PROMPT)))
    assertEquals(COMPLETIONS_SUMMARY_RESPONSE, response.content)

    // Use delays since storing the usage is done in a separate coroutine
    delay(200)
    val usage =
      app.services.admin.listTokenUsage(agentId = agent.id).items.find {
        it.usageType == TokenUsageType.COMPLETION_SUMMARY
      }!!
    assertEquals("test", usage.origin.type)
    assertEquals(chat.id, usage.origin.id)
    assertEquals(admin.id, usage.userId)
    assertEquals(agent.id, usage.agentId)
  }
}
