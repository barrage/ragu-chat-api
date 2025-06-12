package net.barrage.llmao.app.workflow.chat

import net.barrage.llmao.app.workflow.chat.model.AgentConfiguration
import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.ContextEnrichment
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType
import net.barrage.llmao.core.workflow.WorkflowAgent
import net.barrage.llmao.core.types.KUUID

/**
 * Implementation of [WorkflowAgent] for custom chat agents.
 *
 * Token usage is always tracked in this instance when possible, the only exception being the stream
 * whose tokens must be counted outside since we only get the usage when it's complete.
 */
class ChatAgent(
  /** Agent ID. */
  val agentId: KUUID,

  /** The agent configuration ID. */
  val configuration: AgentConfiguration,

  /** Agent name. */
  val name: String,

  /** Obtained from the global settings. */
  private val titleMaxTokens: Int,
  inferenceProvider: InferenceProvider,
  completionParameters: ChatCompletionBaseParameters,
  tokenTracker: TokenUsageTracker,
  history: ChatHistory,
  contextEnrichment: List<ContextEnrichment>? = null,
) :
  WorkflowAgent(
    inferenceProvider = inferenceProvider,
    completionParameters = completionParameters,
    tokenTracker = tokenTracker,
    history = history,
    contextEnrichment = contextEnrichment,
  ) {

  override fun errorMessage(): String = configuration.agentInstructions.errorMessage()

  /**
   * Prompt the LLM using this agent's title instruction as the system message, or the default one
   * if it doesn't has one set. Uses the `prompt` and `response` to generate a title that
   * encapsulates the interaction in a short phrase.
   *
   * The response will largely depend on the system message set by the instruction.
   */
  suspend fun createTitle(prompt: String, response: String): String {
    val titleInstruction = configuration.agentInstructions.titleInstruction()
    val userMessage = "USER: $prompt\nASSISTANT: $response"
    val messages = listOf(ChatMessage.system(titleInstruction), ChatMessage.user(userMessage))

    val completion =
      inferenceProvider.chatCompletion(
        messages,
        ChatCompletionParameters(completionParameters.copy(maxTokens = titleMaxTokens)),
      )

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION_TITLE,
        model = completionParameters.model,
        provider = inferenceProvider.id(),
      )
    }

    // Safe to !! because we are not sending tools to the LLM
    val titleContent = completion.choices.first().message.content!!

    var title = (titleContent as ContentSingle).content

    while (title.startsWith("\"") && title.endsWith("\"") && title.length > 1) {
      title = title.substring(1, title.length - 1)
    }

    LOG.trace("Title generated: {}", title)

    return title
  }
}

/** Wrapper for all the info an agent needs to perform RAG. */
data class ChatAgentCollection(
  /** Collection name. */
  val name: String,

  /** Max amount of results to return when querying. */
  val amount: Int,

  /** The instruction to prepend to the collection data. */
  val instruction: String,

  /** Filter any results above this distance. */
  val maxDistance: Double?,

  /** The embedding provider used to embed the query. */
  val embeddingProvider: String,

  /** The model to use for embeddings. */
  val embeddingModel: String,

  /** Which vector database implementation is used to store the vectors. */
  val vectorProvider: String,
)
