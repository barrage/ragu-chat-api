package net.barrage.llmao.core.services

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.toSessionAgent
import net.barrage.llmao.core.session.chat.ChatSessionAgent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatSessionAgentTests : IntegrationTest(useWiremock = true) {
  private lateinit var service: ChatSessionAgent

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
          titleInstruction = "Custom title instruction",
          summaryInstruction = "Custom summary instruction",
        )
      chat = postgres.testChat(admin.id, agent.id, null)
      val sessionAgent =
        AgentFull(agent, configuration = agentConfiguration, collections = listOf())
          .toSessionAgent()
      service = ChatSessionAgent(app.providers, sessionAgent)
    }
  }

  @Test
  fun successfullyGeneratesChatTitle() = test {
    // Title responses are always the same regardless of the prompt
    val response = service.createTitle("Test prompt - title", "Test response - title")
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response)
  }

  @Test
  fun successfullyStreamsChat() = test {
    // To trigger streams, the following prompt has to be somewhere the message
    val stream = service.chatCompletionStreamWithRag(COMPLETIONS_STREAM_PROMPT, listOf())
    val response = stream.toList().joinToString("") { chunk -> chunk.content ?: "" }
    assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
  }

  @Test
  fun successfullyCompletesChat() = test {
    // To trigger direct responses, the following prompt has to be somewhere the message
    val response = service.chatCompletionWithRag(COMPLETIONS_COMPLETION_PROMPT, listOf())
    assertEquals(COMPLETIONS_RESPONSE, response.content)
  }

  @Test
  fun prepareChatPromptWithRagCorrectlyIncorporatesAgentInstructions() = test {
    val prompt = "What is the capital of Croatia?"
    val history = listOf<ChatMessage>()

    val preparedPrompt = service.prepareChatPromptWithRag(prompt, history)

    assertTrue(preparedPrompt[1].content.contains("What is the capital of Croatia?"))
  }

  @Test
  fun prepareChatPromptWithRagUsesDefaultAgentInstructions() = test {
    val prompt = "What is the capital of Croatia?"
    val history = listOf<ChatMessage>()

    val preparedPrompt = service.prepareChatPromptWithRag(prompt, history)

    assertTrue(preparedPrompt[1].content.contains("What is the capital of Croatia?"))
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
  fun createTitleCorrectlyIncorporatesAgentInstructions() = test {
    val prompt = "What are the best places to visit in Paris?"

    val response =
      "The Eiffel Tower, the Louvre, and the Notre-Dame Cathedral are some of the best places to visit in Paris."

    val titlePrompt = agentConfiguration.agentInstructions.formatTitlePrompt(prompt, response)

    assertTrue(titlePrompt.contains("Custom title instruction"))
    assertTrue(titlePrompt.contains(prompt))
    assertTrue(titlePrompt.contains(response))
  }

  @Test
  fun createTitleUsesDefaultAgentInstructions() = test {
    val defaultAgentConfiguration =
      postgres.testAgentConfiguration(agent.id, llmProvider = "openai", model = "gpt-4o")

    val prompt = "What are the best places to visit in Paris?"

    val response =
      "The Eiffel Tower, the Louvre, and the Notre-Dame Cathedral are some of the best places to visit in Paris."

    val titlePrompt =
      defaultAgentConfiguration.agentInstructions.formatTitlePrompt(prompt, response)

    assertTrue(titlePrompt.contains("Create a short title based on the examples below"))
    assertTrue(titlePrompt.contains(prompt))
    assertTrue(titlePrompt.contains(response))
  }
}
