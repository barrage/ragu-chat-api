package net.barrage.llmao.core.services

import kotlinx.coroutines.flow.toList
import net.barrage.llmao.COMPLETIONS_COMPLETION_PROMPT
import net.barrage.llmao.COMPLETIONS_RESPONSE
import net.barrage.llmao.COMPLETIONS_STREAM_PROMPT
import net.barrage.llmao.COMPLETIONS_STREAM_RESPONSE
import net.barrage.llmao.COMPLETIONS_TITLE_RESPONSE
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ConversationServiceTests : IntegrationTest(useWiremock = true) {
  private lateinit var service: ConversationService

  private lateinit var admin: User
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var chat: Chat

  @BeforeAll
  fun setup() {
    service = app.services.conversation

    admin = postgres.testUser(admin = true)
    agent = postgres.testAgent()
    agentConfiguration =
      postgres.testAgentConfiguration(
        agent.id,
        llmProvider = "openai",
        model = "gpt-4o",
        promptInstruction = "Custom prompt instruction",
        titleInstruction = "Custom title instruction",
        languageInstruction = "Custom language instruction",
        summaryInstruction = "Custom summary instruction",
      )
    chat = postgres.testChat(admin.id, agent.id, null)
  }

  @Test
  fun successfullyGeneratesChatTitle() = test {
    // Title responses are always the same regardless of the prompt
    val response =
      service.createAndUpdateTitle(
        chat.id,
        "Test prompt - title",
        "Test response - title",
        agent.id,
      )
    assertEquals(COMPLETIONS_TITLE_RESPONSE, response)
  }

  @Test
  fun successfullyStreamsChat() = test {
    // To trigger streams, the following prompt has to be somewhere the message
    val stream = service.chatCompletionStream(COMPLETIONS_STREAM_PROMPT, listOf(), agent.id)
    val response =
      stream.toList().joinToString("") { chunk -> chunk.joinToString { it.content ?: "" } }
    assertEquals(COMPLETIONS_STREAM_RESPONSE, response)
  }

  @Test
  fun successfullyCompletesChat() = test {
    // To trigger direct responses, the following prompt has to be somewhere the message
    val response = service.chatCompletion(COMPLETIONS_COMPLETION_PROMPT, listOf(), agent.id)
    assertEquals(COMPLETIONS_RESPONSE, response)
  }

  @Test
  fun prepareChatPromptCorrectlyIncorporatesAgentInstructions() = test {
    val prompt = "What is the capital of Croatia?"
    val history = listOf<ChatMessage>()

    val preparedPrompt = service.prepareChatPrompt(prompt, agentConfiguration, emptyList(), history)

    assertTrue(preparedPrompt[0].content.contains("Custom language instruction"))

    assertTrue(preparedPrompt[1].content.contains("Custom prompt instruction"))

    assertTrue(preparedPrompt[1].content.contains("What is the capital of Croatia?"))
  }

  @Test
  fun prepareChatPromptUsesDefaultAgentInstructions() = test {
    val defaultAgentConfiguration =
      postgres.testAgentConfiguration(agent.id, llmProvider = "openai", model = "gpt-4o")

    val prompt = "What is the capital of Croatia?"
    val history = listOf<ChatMessage>()

    val preparedPrompt =
      service.prepareChatPrompt(prompt, defaultAgentConfiguration, emptyList(), history)

    assertTrue(
      preparedPrompt[0].content.contains("You do not speak any language other than english.")
    )

    assertTrue(
      preparedPrompt[1]
        .content
        .contains(
          "Use the instructions surrounded by triple quotes to respond to the prompt surrounded by triple quotes."
        )
    )

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

    val summaryPrompt = agentConfiguration.agentInstructions.summary(conversationHistory)

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

    val summaryPrompt = defaultAgentConfiguration.agentInstructions.summary(conversationHistory)

    assertTrue(
      summaryPrompt.contains(
        "Create a summary for the conversation below denoted by triple quotes."
      )
    )
    assertTrue(summaryPrompt.contains(conversationHistory))
  }

  @Test
  fun createAndUpdateTitleCorrectlyIncorporatesAgentInstructions() = test {
    val prompt = "What are the best places to visit in Paris?"

    val response =
      "The Eiffel Tower, the Louvre, and the Notre-Dame Cathedral are some of the best places to visit in Paris."

    val titlePrompt = agentConfiguration.agentInstructions.title(prompt, response)

    assertTrue(titlePrompt.contains("Custom title instruction"))
    assertTrue(titlePrompt.contains(prompt))
    assertTrue(titlePrompt.contains(response))
  }

  @Test
  fun createAndUpdateTitleUsesDefaultAgentInstructions() = test {
    val defaultAgentConfiguration =
      postgres.testAgentConfiguration(agent.id, llmProvider = "openai", model = "gpt-4o")

    val prompt = "What are the best places to visit in Paris?"

    val response =
      "The Eiffel Tower, the Louvre, and the Notre-Dame Cathedral are some of the best places to visit in Paris."

    val titlePrompt = defaultAgentConfiguration.agentInstructions.title(prompt, response)

    assertTrue(titlePrompt.contains("Create a short title based on the examples below"))
    assertTrue(titlePrompt.contains(prompt))
    assertTrue(titlePrompt.contains(response))
  }
}
