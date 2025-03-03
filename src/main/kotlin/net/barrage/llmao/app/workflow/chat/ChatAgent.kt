package net.barrage.llmao.app.workflow.chat

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.History
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.llm.collectToolCalls
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.tokens.TokenUsageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/**
 * Handles LLM interactions for direct prompts without streaming and comes with utilities for
 * generating titles.
 *
 * Token usage is always tracked in this instance when possible, the only exception being the stream
 * whose tokens must be counted outside since we only get the usage when it's complete.
 */
class ChatAgent(
  /** Has to be kept here because collections can be from different providers. */
  internal val providers: ProviderState,

  /** Agent ID. */
  val agentId: KUUID,

  /** Agent name. */
  val name: String,

  /** The LLM. */
  val model: String,

  /** LLM provider. */
  val llmProvider: String,

  /** The context, i.e. system message. */
  val context: String,

  /** Collections to use for RAG. */
  val collections: List<ChatAgentCollection>,

  /** Instructions for the agent. */
  val instructions: AgentInstructions,

  /** LLM tools. */
  internal val toolchain: Toolchain?,

  /** The agent configuration ID. */
  val configurationId: KUUID,

  /** Loaded either directly from the agent model or from the global settings. */
  internal val completionParameters: ChatCompletionParameters,

  /** Obtained from the global settings. */
  private val titleMaxTokens: Int,

  /** Used to track token usage. */
  internal val tokenTracker: TokenUsageTracker,

  /** The chat history. */
  internal val history: History,
) {

  /** Available tools. */
  val tools: List<ToolDefinition>? = toolchain?.listToolSchemas()

  suspend fun chatCompletionWithRag(input: List<ChatMessage>): ChatMessage {
    val messages = input.toMutableList()
    val userMessage =
      messages.lastOrNull { it.role == "user" }
        ?: throw AppError.internal("No user message in input")

    // Safe to !! because user messages are never created with null content
    userMessage.content = executeRetrievalAugmentation(userMessage.content!!)

    val systemMessage = ChatMessage.system(context)

    val llmInput = mutableListOf(systemMessage, *messages.toTypedArray())

    val llm = providers.llm.getProvider(llmProvider)

    val completion = llm.chatCompletion(llmInput, completionParameters)

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION,
        model = model,
        provider = llmProvider,
      )
    }

    return completion.choices.first().message
  }

  suspend fun createTitle(prompt: String, response: String): ChatMessage {
    val llm = providers.llm.getProvider(llmProvider)
    val titleInstruction = instructions.titleInstruction()
    val userMessage = "USER: $prompt\nASSISTANT: $response"
    val messages = listOf(ChatMessage.system(titleInstruction), ChatMessage.user(userMessage))

    val completion =
      llm.chatCompletion(messages, completionParameters.copy(maxTokens = titleMaxTokens))

    // Safe to !! because we are not sending tools to the LLM
    var title = completion.choices.first().message.content!!.trim()

    while (title.startsWith("\"") && title.endsWith("\"") && title.length > 1) {
      title = title.substring(1, title.length - 1)
    }

    LOG.trace("Title generated: {}", title)

    completion.choices.first().message.content = title

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION_TITLE,
        model = model,
        provider = llmProvider,
      )
    }

    return completion.choices.first().message
  }

  internal fun addToHistory(messages: List<ChatMessage>) {
    history.add(messages)
  }

  /**
   * Uses the agent's collection setup to retrieve related content from the vector database. Returns
   * a string in the form of:
   * ```
   * <INSTRUCTIONS>
   * <PROMPT>
   * ```
   */
  internal suspend fun executeRetrievalAugmentation(prompt: String): String {
    if (collections.isEmpty()) {
      return prompt
    }

    val systemMessage = ChatMessage.system(context)

    LOG.trace("Created system message {}", systemMessage)

    var collectionInstructions = ""

    // Maps providers to lists of CollectionQuery
    val providerQueries = mutableMapOf<String, MutableList<CollectionQuery>>()

    // Embed the input per collection provider and model
    for (collection in collections) {
      val embeddings =
        providers.embedding
          .getProvider(collection.embeddingProvider)
          .embed(prompt, collection.embeddingModel)

      embeddings.tokensUsed?.let { tokenUsage ->
        tokenTracker.store(
          amount = tokenUsage,
          usageType = TokenUsageType.EMBEDDING,
          model = collection.embeddingModel,
          provider = collection.embeddingProvider,
        )
      }

      if (!providerQueries.containsKey(collection.vectorProvider)) {
        providerQueries[collection.vectorProvider] =
          mutableListOf(CollectionQuery(collection.name, collection.amount, embeddings.embeddings))
      } else {
        providerQueries[collection.vectorProvider]!!.add(
          CollectionQuery(collection.name, collection.amount, embeddings.embeddings)
        )
      }
    }

    // Holds Provider -> Collection -> VectorData
    val relatedChunks = mutableMapOf<String, Map<String, List<VectorData>>>()

    // Query each vector provider for the most similar vectors
    providerQueries.forEach { (provider, queries) ->
      val vectorDb = providers.vector.getProvider(provider)
      relatedChunks[provider] = vectorDb.query(queries)
    }

    for (collection in collections) {
      val instruction = collection.instruction
      // Safe to !! because the providers must be present here if they were mapped above
      val collectionData =
        relatedChunks[collection.vectorProvider]!![collection.name]?.joinToString("\n") {
          it.content
        }

      collectionData?.let {
        collectionInstructions += "$instruction\n\"\"\n\t$collectionData\n\"\""
      }
    }

    val instructions =
      if (collectionInstructions.isEmpty()) ""
      else "Instructions: ${"\"\"\""}\n$collectionInstructions\n${"\"\"\""}"

    return "${instructions}\n$prompt"
  }

  private fun getEncoder(llm: String): Encoding {
    val registry = Encodings.newDefaultEncodingRegistry()
    for (type in ModelType.entries) {
      if (type.name == llm) {
        return registry.getEncodingForModel(type)
      }
    }
    throw AppError.api(ErrorReason.InvalidParameter, "Cannot find tokenizer for model '$llm'")
  }
}

/** Chat agent implementation that can hook up to an emitter and emit message chunks. */
class ChatAgentStreaming(
  val chatAgent: ChatAgent,

  /** Output handle. Only chat related events are sent via this reference. */
  private val emitter: Emitter<ChatWorkflowMessage>,
) {
  /**
   * Recursively calls the chat completion stream until no tool calls are returned.
   *
   * If the agent contains no tools, the response content will be streamed on the first call and
   * this function will be called only once.
   *
   * If the agent contains tools and decides to use them, it will usually stream only the tool calls
   * as the initial response. The tools must then be called and their results sent back to the LLM.
   * This process is repeated until the LLM outputs a final text response.
   *
   * The final output of the LLM will be captured in `out`.
   *
   * @param messageBuffer A buffer that keeps track of new messages that are not yet persisted. Has
   *   to contain the user message.
   * @param out A buffer to capture the final response of the LLM.
   * @param attempt Current attempt. Used to prevent infinite loops.
   */
  suspend fun stream(
    /**
     * Messages not yet persisted nor added to the history. This buffer is handled internally by
     * this function and will contain a list of all the messages in the streaming interaction,
     * including the user message.
     */
    messageBuffer: MutableList<ChatMessage>,

    /**
     * Captures the final response of the LLM. Needs to be passed in since streams can get cancelled
     * and we need to persist unfinished responses.
     */
    out: StringBuilder,

    /** Current execution attempt, used to prevent infinite loops. */
    attempt: Int = 0,
  ) {
    LOG.debug("{} - starting stream (attempt: {})", chatAgent.agentId, attempt + 1)

    // I seriously don't understand why this is needed but it is
    // the only way I've found to not alter the original messages
    // this can't be good I just don't get it
    val llmInput = mutableListOf<ChatMessage>()
    for (message in chatAgent.history) {
      llmInput.add(message.copy())
    }
    for (message in messageBuffer) {
      llmInput.add(message.copy())
    }

    val llmStream =
      chatCompletionStream(
        input = llmInput,
        useRag = true,
        // Stop sending tools if we are "stuck" in a loop
        useTools = attempt < 5 && chatAgent.toolchain != null,
      )

    val toolCalls: MutableMap<Int, ToolCallData> = mutableMapOf()

    llmStream.collect { chunk ->
      // Chunks with some content indicate this is a text chunk
      // and should be emitted.
      if (!chunk.content.isNullOrEmpty()) {
        emitter.emit(ChatWorkflowMessage.StreamChunk(chunk.content))
        out.append(chunk.content)
      }

      chunk.tokenUsage?.let { tokenUsage ->
        chatAgent.tokenTracker.store(
          amount = tokenUsage,
          usageType = TokenUsageType.COMPLETION,
          model = chatAgent.model,
          provider = chatAgent.llmProvider,
        )
      }

      collectToolCalls(toolCalls, chunk)
    }

    // If the coroutine gets cancelled here, we are certain `out` will be cleared.

    // I may have seen some messages that have both tool calls and content
    // but I cannot confirm nor deny this
    assert(out.isNotEmpty() && toolCalls.isEmpty() || toolCalls.isNotEmpty() && out.isBlank())

    messageBuffer.add(
      ChatMessage.assistant(
        content = if (out.isEmpty()) null else out.toString(),
        toolCalls = if (toolCalls.isEmpty()) null else toolCalls.values.toList(),
      )
    )
    out.clear()

    if (toolCalls.isEmpty()) {
      return
    }

    // From this point on, we are handling tool calls and we need to re-prompt the LLM with
    // their results.

    LOG.debug(
      "{} - calling tools: {}",
      chatAgent.agentId,
      toolCalls.values.joinToString(", ") { it.name },
    )

    for (toolCall in toolCalls.values) {
      // Safe to !! because toolchain is not null if toolCalls is not empty
      try {
        val result = chatAgent.toolchain!!.processToolCall(toolCall)
        messageBuffer.add(ChatMessage.toolResult(result.content, toolCall.id))
      } catch (e: Throwable) {
        messageBuffer.add(ChatMessage.toolResult("error: ${e.message}", toolCall.id))
      }
    }

    stream(messageBuffer = messageBuffer, out = out, attempt = attempt + 1)
  }

  /** Start a chat completion stream using the parameters from the WorkflowAgent. */
  private suspend fun chatCompletionStream(
    input: List<ChatMessage>,
    useRag: Boolean = true,
    useTools: Boolean = true,
  ): Flow<ChatMessageChunk> {
    val llmInput = input.toMutableList()

    val llm = chatAgent.providers.llm.getProvider(chatAgent.llmProvider)

    if (useRag) {
      val userMessage =
        llmInput.lastOrNull { it.role == "user" }
          ?: throw AppError.internal("No user message in input")

      // Safe to !! because user messages are never created with null content
      userMessage.content = chatAgent.executeRetrievalAugmentation(userMessage.content!!)
    }

    llmInput.add(0, ChatMessage.system(chatAgent.context))

    return llm.completionStream(
      llmInput,
      chatAgent.completionParameters.copy(tools = if (useTools) chatAgent.tools else null),
    )
  }
}

data class ChatAgentCollection(
  val name: String,
  val amount: Int,
  val instruction: String,
  val embeddingProvider: String,
  val embeddingModel: String,
  val vectorProvider: String,
)

fun AgentFull.toChatAgent(
  history: History,
  providers: ProviderState,
  toolchain: Toolchain?,
  /** Used for default values if the agent configuration does not specify them. */
  settings: ApplicationSettings,
  tokenTracker: TokenUsageTracker,
) =
  ChatAgent(
    agentId = agent.id,
    name = agent.name,
    model = configuration.model,
    llmProvider = configuration.llmProvider,
    context = configuration.context,
    collections =
      collections.map {
        ChatAgentCollection(
          it.collection,
          it.amount,
          it.instruction,
          it.embeddingProvider,
          it.embeddingModel,
          it.vectorProvider,
        )
      },
    instructions = configuration.agentInstructions,
    completionParameters =
      ChatCompletionParameters(
        model = configuration.model,
        temperature = configuration.temperature,
        presencePenalty =
          configuration.presencePenalty ?: settings[SettingKey.AGENT_PRESENCE_PENALTY].toDouble(),
        maxTokens =
          configuration.maxCompletionTokens
            ?: settings.getOptional(SettingKey.AGENT_MAX_COMPLETION_TOKENS)?.toInt(),
      ),
    configurationId = configuration.id,
    providers = providers,
    toolchain = toolchain,
    titleMaxTokens = settings[SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS].toInt(),
    tokenTracker = tokenTracker,
    history = history,
  )

fun ChatAgent.toStreaming(emitter: Emitter<ChatWorkflowMessage>) = ChatAgentStreaming(this, emitter)
