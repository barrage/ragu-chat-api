package net.barrage.llmao.core.workflow.chat

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/** Handles LLM interactions for direct prompts. */
class ChatAgent(
  private val providers: ProviderState,
  val id: KUUID,
  val name: String,
  val model: String,
  val llmProvider: String,
  val context: String,
  val collections: List<ChatAgentCollection>,
  val instructions: AgentInstructions,
  var tools: List<ToolDefinition>? = null,
  val temperature: Double,

  /** The agent configuration ID. */
  val configurationId: KUUID,
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
      ChatCompletionParameters(
        model = model,
        temperature = temperature,
        tools = if (!useTools) null else tools,
      ),
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

    return llm.chatCompletion(llmInput, ChatCompletionParameters(model, temperature))
  }

  suspend fun summarizeConversation(history: List<ChatMessage>): String {
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
        return@joinToString "$s: ${it.content}"
      }

    val summaryPrompt = instructions.formatSummaryPrompt(conversation)

    val messages = listOf(ChatMessage.user(summaryPrompt))

    val completion =
      llm.chatCompletion(
        messages,
        ChatCompletionParameters(model = model, temperature = temperature, maxTokens = 2000),
      )

    // Safe to !! because we are not sending any tools in the message, which means the content
    // will never be null
    return completion.content!!
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

      if (!providerQueries.containsKey(collection.vectorProvider)) {
        providerQueries[collection.vectorProvider] =
          mutableListOf(CollectionQuery(collection.name, collection.amount, embeddings))
      } else {
        providerQueries[collection.vectorProvider]!!.add(
          CollectionQuery(collection.name, collection.amount, embeddings)
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

  suspend fun createTitle(prompt: String, response: String): String {
    val llm = providers.llm.getProvider(llmProvider)
    val titleInstruction = instructions.titleInstruction()
    val userMessage = "PROMPT: $prompt\nRESPONSE: $response"
    val messages = listOf(ChatMessage.system(titleInstruction), ChatMessage.user(userMessage))

    val completion =
      llm.chatCompletion(messages, ChatCompletionParameters(model, temperature, maxTokens = 100))

    // Safe to !! because we are not sending tools to the LLM
    var title = completion.content!!.trim()

    while (title.startsWith("\"") && title.endsWith("\"") && title.length > 1) {
      title = title.substring(1, title.length - 1)
    }

    LOG.trace("Title generated: {}", title)

    return title
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
