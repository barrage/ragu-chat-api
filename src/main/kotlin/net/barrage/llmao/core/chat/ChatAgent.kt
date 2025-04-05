package net.barrage.llmao.core.chat

import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.ServiceState
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.ContextEnrichment
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.model.AgentInstructions
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.WorkflowAgent

private val LOG = KtorSimpleLogger("net.barrage.llmao.core.chat.ChatAgent")

/**
 * Handles LLM interactions for direct prompts without streaming and comes with utilities for
 * generating titles.
 *
 * Token usage is always tracked in this instance when possible, the only exception being the stream
 * whose tokens must be counted outside since we only get the usage when it's complete.
 */
class ChatAgent(
  /** Agent ID. */
  val agentId: KUUID,

  /** The agent configuration ID. */
  val configurationId: KUUID,

  /** Agent name. */
  val name: String,

  /** Instructions for specific chat related tasks. */
  val instructions: AgentInstructions,

  /** Obtained from the global settings. */
  private val titleMaxTokens: Int,
  private val context: String,
  private val emitter: Emitter<ChatWorkflowMessage>? = null,
  user: User,
  model: String,
  llmProvider: LlmProvider,
  completionParameters: ChatCompletionParameters,
  toolchain: Toolchain<ServiceState>?,
  tokenTracker: TokenUsageTracker,
  history: ChatHistory,
  attachmentProcessor: ChatMessageProcessor,
  contextEnrichment: List<ContextEnrichment>? = null,
) :
  WorkflowAgent<ServiceState>(
    user = user,
    model = model,
    llmProvider = llmProvider,
    completionParameters = completionParameters,
    toolchain = toolchain,
    tokenTracker = tokenTracker,
    history = history,
    messageProcessor = attachmentProcessor,
    contextEnrichment = contextEnrichment,
  ) {
  override suspend fun onStreamChunk(chunk: ChatMessageChunk) {
    if (!chunk.content.isNullOrEmpty()) {
      emitter?.emit(ChatWorkflowMessage.StreamChunk(chunk.content))
    }
  }

  override suspend fun onMessage(message: ChatMessage) {
    message.content?.let { emitter?.emit(ChatWorkflowMessage.Response(it.text())) }
  }

  override fun errorMessage(): String {
    return instructions.errorMessage()
  }

  override fun id(): String = agentId.toString()

  override fun context(): String {
    return context
  }

  /**
   * Prompt the LLM using this agent's title instruction as the system message, or the default one
   * if it doesn't has one set. Uses the `prompt` and `response` to generate a title that
   * encapsulates the interaction in a short phrase.
   *
   * The response will largely depend on the system message set by the instruction.
   */
  suspend fun createTitle(prompt: String, response: String): String {
    val titleInstruction = instructions.titleInstruction()
    val userMessage = "USER: $prompt\nASSISTANT: $response"
    val messages = listOf(ChatMessage.system(titleInstruction), ChatMessage.user(userMessage))

    val completion =
      this@ChatAgent.llmProvider.chatCompletion(
        messages,
        completionParameters.copy(maxTokens = titleMaxTokens),
      )

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION_TITLE,
        model = model,
        provider = this@ChatAgent.llmProvider.id(),
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
