package net.barrage.llmao.core.services

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.FinishReason
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private val LOG = KtorSimpleLogger("net.barrage.llmao.core.services.ConversationService")

/** Handles LLM interactions. */
class ConversationService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val chatRepository: ChatRepository,
) {
  suspend fun getChat(chatId: KUUID, pagination: Pagination): ChatWithMessages {
    return chatRepository.getWithMessages(chatId, pagination)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat with ID '$chatId'")
  }

  suspend fun chatCompletionStream(
    prompt: String,
    history: List<ChatMessage>,
    agentId: KUUID,
  ): Flow<List<TokenChunk>> {
    val agentFull = agentRepository.get(agentId)

    val query = prepareChatPrompt(prompt, agentFull.configuration, agentFull.collections, history)

    val llm = providers.llm.getProvider(agentFull.configuration.llmProvider)

    return llm.completionStream(
      query,
      LlmConfig(agentFull.configuration.model, agentFull.configuration.temperature),
    )
  }

  suspend fun chatCompletion(prompt: String, history: List<ChatMessage>, agentId: KUUID): String {
    val agentFull = agentRepository.get(agentId)

    val query = prepareChatPrompt(prompt, agentFull.configuration, agentFull.collections, history)

    val llm = providers.llm.getProvider(agentFull.configuration.llmProvider)

    return llm.chatCompletion(
      query,
      LlmConfig(agentFull.configuration.model, agentFull.configuration.temperature),
    )
  }

  suspend fun processMessagePair(
    chatId: KUUID,
    userId: KUUID,
    agentId: KUUID,
    userPrompt: String,
    llmResponse: String,
  ): Pair<Message, Message> {
    val userMessage = chatRepository.insertUserMessage(chatId, userId, userPrompt)

    val agentFull = agentRepository.get(agentId)
    val assistantMessage =
      chatRepository.insertAssistantMessage(
        chatId,
        agentFull.configuration.id,
        llmResponse,
        userMessage.id,
      )

    return Pair(userMessage, assistantMessage)
  }

  suspend fun processFailedMessage(
    chatId: KUUID,
    userId: KUUID,
    prompt: String,
    reason: FinishReason,
  ) {
    chatRepository.insertFailedMessage(chatId, userId, prompt, reason.value)
  }

  suspend fun summarizeConversation(
    chatId: KUUID,
    history: List<ChatMessage>,
    agentId: KUUID,
  ): String {
    val agentFull = agentRepository.get(agentId)

    val llm = providers.llm.getProvider(agentFull.configuration.llmProvider)

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

    val summaryPrompt = agentFull.configuration.agentInstructions.summary(conversation)

    val summary =
      llm.summarizeConversation(
        summaryPrompt,
        LlmConfig(agentFull.configuration.model, agentFull.configuration.temperature),
      )

    chatRepository.insertSystemMessage(chatId, summary)

    return summary
  }

  suspend fun countHistoryTokens(history: List<ChatMessage>, agentId: KUUID): Int {
    val agentFull = agentRepository.get(agentId)
    val text = history.joinToString("\n") { it.content }
    return getEncoder(agentFull.configuration.model).encode(text).size()
  }

  suspend fun prepareChatPrompt(
    prompt: String,
    agentConfig: AgentConfiguration,
    collections: List<AgentCollection>,
    history: List<ChatMessage>,
  ): List<ChatMessage> {
    val systemMessage = ChatMessage.system(agentConfig.context)

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
          mutableListOf(CollectionQuery(collection.collection, collection.amount, embeddings))
      } else {
        providerQueries[collection.vectorProvider]!!.add(
          CollectionQuery(collection.collection, collection.amount, embeddings)
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
        relatedChunks[collection.vectorProvider]!![collection.collection]?.joinToString("\n") {
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

    val message = "${instructions}Prompt: ${"\"\"\""}\n$prompt\n${"\"\"\""}"

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

  suspend fun storeChat(id: KUUID, userId: KUUID, agentId: KUUID, title: String?) {
    chatRepository.insert(id, userId, agentId, title)
  }

  suspend fun createAndUpdateTitle(
    chatId: KUUID,
    prompt: String,
    response: String,
    agentId: KUUID,
  ): String {
    val agentFull = agentRepository.get(agentId)

    val titlePrompt = agentFull.configuration.agentInstructions.title(prompt, response)

    LOG.trace("Created title prompt:\n{}", titlePrompt)

    val llm = providers.llm.getProvider(agentFull.configuration.llmProvider)
    var title =
      llm
        .generateChatTitle(
          titlePrompt,
          LlmConfig(agentFull.configuration.model, agentFull.configuration.temperature),
        )
        .trim()

    while (title.startsWith("\"") && title.endsWith("\"") && title.length > 1) {
      title = title.substring(1, title.length - 1)
    }

    LOG.trace("Title generated: {}", title)
    chatRepository.updateTitle(chatId, title)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    return title
  }

  suspend fun getChatMaxHistory(): Int {
    return chatRepository.getMaxHistory()
  }
}
