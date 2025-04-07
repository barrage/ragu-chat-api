package net.barrage.llmao.core.services

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
import net.barrage.llmao.core.model.DEFAULT_TITLE_INSTRUCTION
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
      chat = postgres.testChat(ADMIN_USER, agent.id, null)
      val agent =
        AgentFull(
          agent,
          configuration = agentConfiguration,
          collections = listOf(),
          groups = listOf(),
        )
      chatAgent = ChatWorkflowFactory.createChatAgent(chat.id, ADMIN_USER, agent)
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
