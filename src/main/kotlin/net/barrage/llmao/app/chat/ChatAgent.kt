package net.barrage.llmao.app.chat

import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.llm.collectToolCalls
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.AgentInstructions
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.core.workflow.Emitter

private const val MAX_TOOL_ATTEMPTS: Int = 5

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

  /** ID of the user who created the chat. Used to link to the user's account on the auth server. */
  val userId: String,

  /**
   * Used to check collection permissions. If the `groups` properties in collections is present,
   * this set will be used to check whether the agent can access that collection.
   *
   * If this set contains a group from the groups property, the agent can access that collection.
   */
  val availableGroups: Set<String>,

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

  /**
   * The chat history. Has to be managed from outside since messages need to be stored, and we can
   * only reason about which messages to add based on whether or not they are successfully stored.
   */
  internal val history: ChatHistory,
  private val attachmentProcessor: ChatMessageProcessor,
) {

  /** Available tools. */
  val tools: List<ToolDefinition>? = toolchain?.listToolSchemas()

  /**
   * Execute chat completion without streaming.
   *
   * @return A list of messages that occurred during inference. If no tools are called, this will
   *   contain the user and assistant message pair. If the agent calls tools, all calls and results
   *   will be captured in between.
   */
  suspend fun completion(
    text: String,
    attachments: List<IncomingMessageAttachment>? = null,
  ): List<ChatMessage> {
    val enrichedText = executeRetrievalAugmentation(text)

    // Content for LLM
    val content =
      attachments?.let { attachmentProcessor.toContentMulti(enrichedText, it) }
        ?: ContentSingle(enrichedText)

    val messageBuffer = mutableListOf<ChatMessage>()

    completion(ChatMessage.user(content), messageBuffer)

    // Original content for storage, without any enrichment
    val originalUserMessage =
      attachments?.let { attachmentProcessor.toContentMulti(text, it) } ?: ContentSingle(text)

    return listOf(ChatMessage.user(originalUserMessage)) + messageBuffer
  }

  /**
   * Inner implementation that runs recursively until no tool calls are returned from the LLM or the
   * maximum attempts are reached.
   *
   * Populates [messageBuffer] with all the messages that occurred during inference.
   *
   * @param userMessage The user message. Passed solely as an immutable reference.
   * @param messageBuffer The message buffer for tracking messages. As this gets called, this will
   *   get populated with all the messages that occurred during inference, including the final
   *   assistant message.
   */
  private suspend fun completion(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    attempt: Int = 0,
  ) {
    LOG.debug("{} - starting completion (attempt: {})", agentId, attempt + 1)

    val llmInput = mutableListOf(ChatMessage.system(context))

    for (message in history) {
      llmInput.add(message.copy())
    }

    llmInput.add(userMessage)

    for (message in messageBuffer) {
      llmInput.add(message.copy())
    }

    val llm = providers.llm.getProvider(llmProvider)

    val completion =
      llm.chatCompletion(
        messages = llmInput,
        config = completionParameters.copy(tools = if (attempt < MAX_TOOL_ATTEMPTS) tools else null),
      )

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION,
        model = model,
        provider = llmProvider,
      )
    }

    val message = completion.choices.first().message

    messageBuffer.add(message)

    if (message.toolCalls != null) {
      LOG.debug(
        "{} - (completion) calling tools: {}",
        agentId,
        message.toolCalls.joinToString(", ") { it.name },
      )

      for (toolCall in message.toolCalls) {
        val result = toolchain!!.processToolCall(toolCall)
        messageBuffer.add(ChatMessage.toolResult(result.content, toolCall.id))
      }

      completion(userMessage, messageBuffer, attempt + 1)
    } else if (message.content == null) {
      LOG.warn("Assistant message has no content and no tools have been called.")
    }
  }

  suspend fun createTitle(prompt: String, response: String): String {
    val llm = providers.llm.getProvider(llmProvider)
    val titleInstruction = instructions.titleInstruction()
    val userMessage = "USER: $prompt\nASSISTANT: $response"
    val messages = listOf(ChatMessage.system(titleInstruction), ChatMessage.user(userMessage))

    val completion =
      llm.chatCompletion(messages, completionParameters.copy(maxTokens = titleMaxTokens))

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION_TITLE,
        model = model,
        provider = llmProvider,
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

    var collectionInstructions = ""

    // Maps providers to lists of CollectionQuery
    val providerQueries = mutableMapOf<String, MutableList<CollectionQuery>>()

    // Embed the input per collection provider and model
    for (collection in collections) {
      val collectionInfo =
        providers.vector.getProvider(collection.vectorProvider).getCollectionInfo(collection.name)

      if (collectionInfo == null) {
        LOG.warn("Collection '{}' does not exist, skipping", collection.name)
        continue
      }

      // Check collection permissions
      var allowed = true

      // No groups assigned means collection is visible to everyone
      collectionInfo.groups?.let {
        // Empty groups also
        if (it.isEmpty()) {
          return@let
        }

        allowed = false

        for (group in it) {
          if (availableGroups.contains(group)) {
            allowed = true
            break
          }
        }
      }

      if (!allowed) {
        LOG.warn(
          "Collection '{}' is not available to user '{}'; required: {}, user: {}",
          collection.name,
          userId,
          collectionInfo.groups,
          availableGroups,
        )
        continue
      }

      val embeddings =
        providers.embedding
          .getProvider(collection.embeddingProvider)
          .embed(prompt, collection.embeddingModel)

      embeddings.usage?.let { tokenUsage ->
        tokenTracker.store(
          amount = tokenUsage,
          usageType = TokenUsageType.EMBEDDING,
          model = collection.embeddingModel,
          provider = collection.embeddingProvider,
        )
      }

      providerQueries[collection.vectorProvider]?.add(
        CollectionQuery(
          name = collection.name,
          amount = collection.amount,
          maxDistance = collection.maxDistance,
          vector = embeddings.embeddings,
        )
      )
        ?: run {
          providerQueries[collection.vectorProvider] =
            mutableListOf(
              CollectionQuery(
                name = collection.name,
                amount = collection.amount,
                maxDistance = collection.maxDistance,
                vector = embeddings.embeddings,
              )
            )
        }
    }

    // Holds Provider -> Collection -> VectorData
    val relatedChunks = mutableMapOf<String, Map<String, List<VectorData>>>()

    // Query each vector provider for the most similar vectors
    providerQueries.forEach { (provider, queries) ->
      val vectorDb = providers.vector.getProvider(provider)
      try {
        relatedChunks[provider] = vectorDb.query(queries)
      } catch (e: Throwable) {
        LOG.error("Failed to query vector database", e)
      }
    }

    for (collection in collections) {
      val instruction = collection.instruction

      if (relatedChunks[collection.vectorProvider] == null) {
        LOG.warn("No results for collection: {}", collection.name)
        continue
      }

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

    return if (instructions.isBlank()) prompt else "$instructions\n$prompt"
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
   * this function will be called only once. Note that each call to the LLM is done in streaming
   * mode.
   *
   * If the agent contains tools and decides to use them, it will usually stream only the tool calls
   * as the initial response. The tools must then be called and their results sent back to the LLM.
   * This process is repeated until the LLM outputs a final text response or the maximum number of
   * tool attempts is reached.
   *
   * The final output of the LLM will be captured in `out`.
   *
   * @param userMessage Immutable reference to the original user message.
   * @param messageBuffer A buffer that keeps track of new messages that are not yet persisted. Gets
   *   populated with all the messages that occurred during inference including the final assistant
   *   response.
   * @param out A buffer to capture the final response of the LLM. If the stream gets cancelled and
   *   this is not empty, it means a manual cancel occurred.
   */
  suspend fun stream(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    out: StringBuilder,
  ) = streamInner(userMessage, messageBuffer, out, 0)

  /**
   * See [stream].
   *
   * @param attempt Current attempt. Used to prevent infinite loops.
   */
  private suspend fun streamInner(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    out: StringBuilder,
    attempt: Int = 0,
  ) {
    // Perform RAG only on the first attempt and swap the initial user message with an enriched one.
    // We need to copy the original since we do not store the enriched message, only the original.
    val userMessage =
      if (attempt == 0) {
        assert(messageBuffer.isEmpty())
        val enrichedText = chatAgent.executeRetrievalAugmentation(userMessage.content!!.text())
        userMessage.copy(content = userMessage.content!!.copyWithText(enrichedText))
      } else userMessage

    LOG.debug("{} - starting stream (attempt: {})", chatAgent.agentId, attempt + 1)

    val llm = chatAgent.providers.llm.getProvider(chatAgent.llmProvider)

    // I seriously don't understand why this is needed but it is
    // the only way I've found to not alter the original messages
    // this can't be good I just don't get it
    val llmInput = mutableListOf<ChatMessage>(ChatMessage.system(chatAgent.context))

    for (message in chatAgent.history) {
      llmInput.add(message.copy())
    }

    llmInput.add(userMessage)

    for (message in messageBuffer) {
      llmInput.add(message.copy())
    }

    val llmStream =
      llm.completionStream(
        llmInput,
        chatAgent.completionParameters.copy(
          tools = if (attempt < MAX_TOOL_ATTEMPTS) chatAgent.tools else null
        ),
      )

    val toolCalls: MutableMap<Int, ToolCallData> = mutableMapOf()

    // The finish reason will get adjusted when the last chunk found.
    // It is guaranteed that the LLM stream will return the finish reason.
    var finishReason: FinishReason? = null

    llmStream.collect { chunk ->
      // Chunks with some content indicate this is a text chunk
      // and should be emitted.
      if (!chunk.content.isNullOrEmpty()) {
        emitter.emit(ChatWorkflowMessage.StreamChunk(chunk.content))
        out.append(chunk.content)
      }

      chunk.stopReason?.let { reason -> finishReason = reason }

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

    // If the coroutine gets cancelled here, we are certain `out` will be cleared and the
    // last message is the assistant's.

    // I may have seen some messages that have both tool calls and content
    // but I cannot confirm nor deny this
    assert(out.isNotEmpty() && toolCalls.isEmpty() || toolCalls.isNotEmpty() && out.isBlank())

    messageBuffer.add(
      ChatMessage.assistant(
        content = if (out.isEmpty()) null else out.toString(),
        toolCalls = if (toolCalls.isEmpty()) null else toolCalls.values.toList(),
        // If the stream gets cancelled during collecting, the method is already cancelled
        // and we never reach this point, so safe to !!
        finishReason = finishReason!!,
      )
    )
    out.clear()

    if (toolCalls.isEmpty()) {
      return
    }

    // From this point on, we are handling tool calls and we need to re-prompt the LLM with
    // their results.

    LOG.debug(
      "{} - (stream) calling tools: {}",
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

    streamInner(
      userMessage = userMessage,
      messageBuffer = messageBuffer,
      out = out,
      attempt = attempt + 1,
    )
  }

  internal fun addToHistory(messages: List<ChatMessage>) {
    chatAgent.history.add(messages)
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

fun AgentFull.toChatAgent(
  userId: String,
  allowedGroups: List<String>,
  history: ChatHistory,
  providers: ProviderState,
  toolchain: Toolchain?,
  completionParameters: ChatCompletionParameters,
  /** Used for default values if the agent configuration does not specify them. */
  settings: ApplicationSettings,
  tokenTracker: TokenUsageTracker,
) =
  ChatAgent(
    userId = userId,
    agentId = agent.id,
    name = agent.name,
    model = configuration.model,
    llmProvider = configuration.llmProvider,
    context = configuration.context,
    collections =
      collections.map {
        ChatAgentCollection(
          name = it.collection,
          amount = it.amount,
          instruction = it.instruction,
          maxDistance = it.maxDistance,
          embeddingProvider = it.embeddingProvider,
          embeddingModel = it.embeddingModel,
          vectorProvider = it.vectorProvider,
        )
      },
    instructions = configuration.agentInstructions,
    completionParameters = completionParameters,
    configurationId = configuration.id,
    providers = providers,
    toolchain = toolchain,
    titleMaxTokens = settings[SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS].toInt(),
    tokenTracker = tokenTracker,
    history = history,
    availableGroups = allowedGroups.toSet(),
    attachmentProcessor = ChatMessageProcessor(providers),
  )

fun ChatAgent.toStreaming(emitter: Emitter<ChatWorkflowMessage>) = ChatAgentStreaming(this, emitter)
