package net.barrage.llmao.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.ADMIN_USER
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_PROMPT
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.workflow.chat.ChatWorkflowFactory
import net.barrage.llmao.core.chat.ChatAgent
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.Chat
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
      chat = postgres.testChat(ADMIN_USER, agent.id, null)
      val agent =
        AgentFull(
          agent,
          configuration = agentConfiguration,
          collections = listOf(),
          groups = listOf(),
        )

      workflow = ChatWorkflowFactory.createChatAgent(chat.id, ADMIN_USER, agent)
    }
  }

  @Test
  fun registersUsageWhenCallingChatCompletion() = test {
    val response = workflow.completion(COMPLETIONS_COMPLETION_PROMPT)
    assertEquals(COMPLETIONS_RESPONSE, response.last().content!!.text())

    // Use delays since storing the usage is done in a separate coroutine
    delay(200)
    val usage =
      app.services.admin.admin.listTokenUsage(agentId = agent.id).items.find {
        it.usageType == TokenUsageType.COMPLETION
      }!!
    assertEquals(CHAT_WORKFLOW_ID, usage.origin.type)
    assertEquals(chat.id, usage.origin.id)
    assertEquals(ADMIN_USER.id, usage.userId)
    assertEquals(agent.id, usage.agentId)
  }

  @Test
  fun registersUsageWhenCallingTitleCompletion() = test {
    val response = workflow.createTitle("foo", "bar")
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response)

    // Use delays since storing the usage is done in a separate coroutine
    delay(200)
    val usage =
      app.services.admin.admin.listTokenUsage(agentId = agent.id).items.find {
        it.usageType == TokenUsageType.COMPLETION_TITLE
      }!!
    assertEquals(CHAT_WORKFLOW_ID, usage.origin.type)
    assertEquals(chat.id, usage.origin.id)
    assertEquals(ADMIN_USER.id, usage.userId)
    assertEquals(agent.id, usage.agentId)
  }
}
