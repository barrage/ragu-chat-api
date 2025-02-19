package net.barrage.llmao.app.workflow.chat

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.tokens.TokenUsageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/**
 * Handles LLM interactions for direct prompts.
 *
 * Token usage is always tracked in this instance when possible, the only exception being the stream
 * whose tokens must be counted outside since we only get the usage when it's complete.
 */
class ChatAgent(
  /** Has to be kept here because collections can be from different providers. */
  private val providers: ProviderState,

  /** Agent ID. */
  val id: KUUID,

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

  /** Available tools. */
  var tools: List<ToolDefinition>? = null,

  /** The agent configuration ID. */
  val configurationId: KUUID,

  /** Loaded either directly from the agent model or from the global settings. */
  private val completionParameters: ChatCompletionParameters,

  /** Obtained from the global settings. */
  private val titleMaxTokens: Int,

  /** Obtained from the global settings. */
  private val summaryMaxTokens: Int,

  /** Used to track token usage. */
  private val tokenTracker: TokenUsageTracker,
) {
  /** Start a chat completion stream using the parameters from the WorkflowAgent. */
  suspend fun chatCompletionStream(
    input: List<ChatMessage>,
    useRag: Boolean = true,
    useTools: Boolean = true,
  ): Flow<ChatMessageChunk> {
    val llmInput = input.toMutableList()

    val llm = providers.llm.getProvider(llmProvider)

    if (useRag) {
      val userMessage =
        llmInput.lastOrNull { it.role == "user" }
          ?: throw AppError.internal("No user message in input")

      // Safe to !! because user messages are never created with null content
      userMessage.content = executeRetrievalAugmentation(userMessage.content!!)
    }

    llmInput.add(0, ChatMessage.system(context))

    return llm.completionStream(
      llmInput,
      completionParameters.copy(tools = if (useTools) tools else null),
    )
  }

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

  suspend fun summarizeConversation(history: List<ChatMessage>): ChatMessage {
    val llm = providers.llm.getProvider(llmProvider)

    val conversation =
      history.joinToString("\n") {
        val s =
          when (it.role) {
            "user" -> "User"
            "assistant" -> "Assistant"
            "system" -> "System"
            else -> ""
          }
        "$s: ${it.content}"
      }

    val summaryPrompt = instructions.formatSummaryPrompt(conversation)

    val messages = listOf(ChatMessage.user(summaryPrompt))

    val completion =
      llm.chatCompletion(messages, completionParameters.copy(maxTokens = summaryMaxTokens))

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION_SUMMARY,
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

  fun countHistoryTokens(history: List<ChatMessage>): Int {
    val text = history.joinToString("\n") { it.content ?: "" }
    return getEncoder(model).encode(text).size()
  }

  /**
   * Uses the agent's collection setup to retrieve related content from the vector database. Returns
   * a string in the form of:
   * ```
   * <INSTRUCTIONS>
   * <PROMPT>
   * ```
   */
  private suspend fun executeRetrievalAugmentation(prompt: String): String {
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

data class ChatAgentCollection(
  val name: String,
  val amount: Int,
  val instruction: String,
  val embeddingProvider: String,
  val embeddingModel: String,
  val vectorProvider: String,
)

fun AgentFull.toChatAgent(
  providers: ProviderState,
  tools: List<ToolDefinition>?,
  /** Used for default values if the agent configuration does not specify them. */
  settings: ApplicationSettings,
  tokenTracker: TokenUsageTracker,
) =
  ChatAgent(
    id = agent.id,
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
    tools = tools,
    titleMaxTokens = settings[SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS].toInt(),
    summaryMaxTokens = settings[SettingKey.AGENT_SUMMARY_MAX_COMPLETION_TOKENS].toInt(),
    tokenTracker = tokenTracker,
  )
