package net.barrage.llmao.core.session.chat

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.MessageChunk
import net.barrage.llmao.core.session.SessionAgent
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/** Handles LLM interactions for direct prompts. */
class ChatSessionAgent(private val providers: ProviderState, val agent: SessionAgent) {
  /** Start a chat completion stream and pass all tools from the toolchain. */
  suspend fun chatCompletionStreamWithTools(
    input: String,
    history: List<ChatMessage>,
  ): Flow<List<MessageChunk>> {

    val messages = history.toMutableList()
    messages.add(ChatMessage.user(input))

    val llm = providers.llm.getProvider(agent.llmProvider)

    return llm.completionStream(
      messages,
      ChatCompletionParameters(
        model = agent.model,
        temperature = agent.temperature,
        tools = agent.toolchain?.listToolSchemas(),
      ),
    )
  }

  /**
   * Start a chat completion using the agent's configuration.
   *
   * This method performs RAG.
   */
  suspend fun chatCompletionStreamWithRag(
    input: String,
    history: List<ChatMessage>,
  ): Flow<List<MessageChunk>> {
    val query = prepareChatPromptWithRag(input, history)

    val llm = providers.llm.getProvider(agent.llmProvider)

    return llm.completionStream(query, ChatCompletionParameters(agent.model, agent.temperature))
  }

  suspend fun chatCompletionWithRag(input: String, history: List<ChatMessage>): ChatMessage {
    val query = prepareChatPromptWithRag(input, history)
    val llm = providers.llm.getProvider(agent.llmProvider)
    return llm.chatCompletion(query, ChatCompletionParameters(agent.model, agent.temperature))
  }

  suspend fun summarizeConversation(history: List<ChatMessage>): String {
    val llm = providers.llm.getProvider(agent.llmProvider)

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

    val summaryPrompt = agent.instructions.formatSummaryPrompt(conversation)

    val messages = listOf(ChatMessage.user(summaryPrompt))

    val completion =
      llm.chatCompletion(
        messages,
        ChatCompletionParameters(
          model = agent.model,
          temperature = agent.temperature,
          maxTokens = 2000,
        ),
      )

    val summary = completion.content

    // chatRepository.insertSystemMessage(chatId, summary)

    return summary
  }

  fun countHistoryTokens(history: List<ChatMessage>): Int {
    val text = history.joinToString("\n") { it.content }
    return getEncoder(agent.model).encode(text).size()
  }

  suspend fun prepareChatPromptWithRag(
    prompt: String,
    history: List<ChatMessage>,
  ): List<ChatMessage> {
    val systemMessage = ChatMessage.system(agent.context)

    LOG.trace("Created system message {}", systemMessage)

    var collectionInstructions = ""

    // Maps providers to lists of CollectionQuery
    val providerQueries = mutableMapOf<String, MutableList<CollectionQuery>>()

    // Embed the input per collection provider and model
    for (collection in agent.collections) {
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

    for (collection in agent.collections) {
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

    val message = userMessage(prompt, collectionInstructions)

    LOG.trace("Created user message {}", message)

    val messages = mutableListOf(systemMessage, *history.toTypedArray(), message)

    return messages
  }

  private fun userMessage(prompt: String, collectionInstructions: String): ChatMessage {
    val instructions =
      if (collectionInstructions.isEmpty()) ""
      else "Instructions: ${"\"\"\""}\n$collectionInstructions\n${"\"\"\""}\n"

    val message = "${instructions}$prompt"

    return ChatMessage.user(message)
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
    val llm = providers.llm.getProvider(agent.llmProvider)

    val titleInstruction = agent.instructions.formatTitlePrompt(prompt, response)

    LOG.trace("Created title prompt:\n{}", titleInstruction)

    val messages = listOf(ChatMessage.user(prompt))

    val completion =
      llm.chatCompletion(messages, ChatCompletionParameters(agent.model, agent.temperature))

    var title = completion.content.trim()

    while (title.startsWith("\"") && title.endsWith("\"") && title.length > 1) {
      title = title.substring(1, title.length - 1)
    }

    LOG.trace("Title generated: {}", title)

    return title
  }
}
