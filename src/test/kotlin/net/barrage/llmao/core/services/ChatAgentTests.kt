package net.barrage.llmao.core.services

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_PROMPT
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.DEFAULT_TITLE_INSTRUCTION
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.toChatAgent
import net.barrage.llmao.core.workflow.chat.ChatAgent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatAgentTests : IntegrationTest(useWiremock = true) {
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
          summaryInstruction = "Custom summary instruction",
        )
      chat = postgres.testChat(admin.id, agent.id, null)
      workflow =
        AgentFull(agent, configuration = agentConfiguration, collections = listOf())
          .toChatAgent(providers = app.providers)
    }
  }

  @Test
  fun successfullyGeneratesChatTitle() = test {
    // Title responses are always the same regardless of the prompt
    val response = workflow.createTitle("Test prompt - title", "Test response - title")
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response)
  }

  @Test
  fun successfullyStreamsChat() = test {
    // To trigger streams, the following prompt has to be somewhere the message
    val stream = workflow.chatCompletionStream(listOf(ChatMessage.user(COMPLETIONS_STREAM_PROMPT)))
    val response = stream.toList().joinToString("") { chunk -> chunk.content ?: "" }
    assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
  }

  @Test
  fun successfullyCompletesChat() = test {
    // To trigger direct responses, the following prompt has to be somewhere the message
    val response =
      workflow.chatCompletionWithRag(listOf(ChatMessage.user(COMPLETIONS_COMPLETION_PROMPT)))
    assertEquals(COMPLETIONS_RESPONSE, response.content)
  }

  @Test
  fun summarizeConversationCorrectlyIncorporatesAgentInstructions() = test {
    val conversationHistory =
      """
      User: What's the weather like today?
      Assistant: It's sunny with a high of 75°F (24°C).
      User: Great! Any chance of rain?
      Assistant: There's a 10% chance of light rain in the evening.
      """
        .trimIndent()

    val summaryPrompt =
      agentConfiguration.agentInstructions.formatSummaryPrompt(conversationHistory)

    assertTrue(summaryPrompt.contains("Custom summary instruction"))
    assertTrue(summaryPrompt.contains(conversationHistory))
  }

  @Test
  fun summarizeConversationUsesDefaultAgentInstructions() = test {
    val defaultAgentConfiguration =
      postgres.testAgentConfiguration(agent.id, llmProvider = "openai", model = "gpt-4o")

    val conversationHistory =
      """
      User: What's the weather like today?
      Assistant: It's sunny with a high of 75°F (24°C).
      User: Great! Any chance of rain?
      Assistant: There's a 10% chance of light rain in the evening.
      """
        .trimIndent()

    val summaryPrompt =
      defaultAgentConfiguration.agentInstructions.formatSummaryPrompt(conversationHistory)

    assertTrue(
      summaryPrompt.contains(
        "Create a summary for the conversation below denoted by triple quotes."
      )
    )
    assertTrue(summaryPrompt.contains(conversationHistory))
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
