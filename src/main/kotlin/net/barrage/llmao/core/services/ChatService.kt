package net.barrage.llmao.core.services

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import io.ktor.server.plugins.*
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.ProviderState
import net.barrage.llmao.core.chat.ChatMessage
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.llm.PromptFormatter
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.apiError
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.ChatWithMessages
import net.barrage.llmao.models.CountedList
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.PaginationSort
import net.barrage.llmao.serializers.KUUID

class ChatService(
  private val providers: ProviderState,
  private val chatRepo: ChatRepository,
  private val agentRepository: AgentRepository,
) {
  fun listChats(pagination: PaginationSort): CountedList<Chat> {
    return chatRepo.getAll(pagination)
  }

  fun listChats(pagination: PaginationSort, userId: KUUID): CountedList<Chat> {
    return chatRepo.getAll(pagination, userId)
  }

  fun getChat(chatId: KUUID): ChatWithMessages? {
    return chatRepo.getWithMessages(chatId)
  }

  fun getMessages(chatId: KUUID): List<Message> {
    return chatRepo.getMessages(chatId)
  }

  fun storeChat(id: KUUID, userId: KUUID, agentId: KUUID, title: String?) {
    chatRepo.insert(id, userId, agentId, title)
  }

  suspend fun generateTitle(
    chatId: KUUID,
    prompt: String,
    formatter: PromptFormatter,
    agentId: KUUID,
  ): String {
    val titlePrompt = formatter.title(prompt)
    val agent =
      agentRepository.get(agentId)
        ?: throw apiError("Entity not found", "Agent with ID '$agentId' does not exist")
    val llm = providers.llm.getProvider(agent.llmProvider)
    val title =
      llm
        .generateChatTitle(titlePrompt, LlmConfig(agent.model, agent.temperature, agent.language))
        .trim()
    chatRepo.updateTitle(chatId, title) ?: throw NotFoundException("Chat not found")
    return title
  }

  fun updateTitle(chatId: KUUID, title: String) {
    chatRepo.updateTitle(chatId, title)
  }

  fun updateTitle(chatId: KUUID, userId: KUUID, title: String) {
    chatRepo.updateTitle(chatId, userId, title)
  }

  suspend fun chatCompletionStream(
    prompt: String,
    history: List<ChatMessage>,
    agentId: KUUID,
    formatter: PromptFormatter,
  ): Flow<List<TokenChunk>> {
    val agent =
      agentRepository.get(agentId)
        ?: throw apiError("Entity not found", "Agent with ID '$agentId' does not exist")

    val collections = agentRepository.getCollections(agentId)

    val vectorDb = providers.vector.getProvider(agent.vectorProvider)
    val llm = providers.llm.getProvider(agent.llmProvider)

    val query = prepareChatPrompt(prompt, history, formatter, collections, vectorDb)

    return llm.completionStream(query, LlmConfig(agent.model, agent.temperature, agent.language))
  }

  suspend fun chatCompletion(
    prompt: String,
    history: List<ChatMessage>,
    agentId: KUUID,
    formatter: PromptFormatter,
  ): String {
    val agent =
      agentRepository.get(agentId)
        ?: throw apiError("Entity not found", "Agent with ID '$agentId' does not exist")

    val collections = agentRepository.getCollections(agentId)

    val vectorDb = providers.vector.getProvider(agent.vectorProvider)
    val llm = providers.llm.getProvider(agent.llmProvider)

    val query = prepareChatPrompt(prompt, history, formatter, collections, vectorDb)
    return llm.chatCompletion(query, LlmConfig(agent.model, agent.temperature, agent.language))
  }

  fun processMessagePair(
    chatId: KUUID,
    userId: KUUID,
    agentId: KUUID,
    userPrompt: String,
    llmResponse: String,
  ): Pair<Message, Message> {
    val userMessage = chatRepo.insertUserMessage(chatId, userId, userPrompt)
    val assistantMessage =
      chatRepo.insertAssistantMessage(chatId, agentId, llmResponse, userMessage.id)

    return Pair(userMessage, assistantMessage)
  }

  fun processFailedMessage(chatId: KUUID, userId: KUUID, prompt: String, reason: String) {
    chatRepo.insertFailedMessage(chatId, userId, prompt, reason)
  }

  private fun prepareChatPrompt(
    prompt: String,
    history: List<ChatMessage>,
    formatter: PromptFormatter,
    queryOptions: List<Pair<String, Int>>,
    vectorDb: VectorDatabase,
  ): List<ChatMessage> {
    val system = formatter.systemMessage()

    val embedded = embedQuery(prompt)

    val relatedChunks = vectorDb.query(embedded, queryOptions)

    val context = relatedChunks.joinToString("\n")

    val query = formatter.userMessage(prompt, context)

    val messages = mutableListOf(system, *history.toTypedArray(), query)

    return messages
  }

  suspend fun summarizeConversation(
    chatId: KUUID,
    history: List<ChatMessage>,
    formatter: PromptFormatter,
    agentId: KUUID,
  ): String {
    val agent =
      agentRepository.get(agentId)
        ?: throw apiError("Entity not found", "Agent with ID '$agentId' does not exist")

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

    val summaryPrompt = formatter.summary(conversation)

    val summary =
      llm.summarizeConversation(
        summaryPrompt,
        LlmConfig(agent.model, agent.temperature, agent.language),
      )

    chatRepo.insertSystemMessage(chatId, summary)

    return summary
  }

  fun deleteChat(id: KUUID) {
    chatRepo.delete(id)
  }

  fun deleteChat(id: KUUID, userId: KUUID) {
    chatRepo.delete(id, userId)
  }

  fun evaluateMessage(chatId: KUUID, messageId: KUUID, evaluation: Boolean): Message {
    chatRepo.getMessage(chatId, messageId) ?: throw NotFoundException("Message not found")
    return chatRepo.evaluateMessage(messageId, evaluation)
      ?: throw NotFoundException("Message not found")
  }

  fun evaluateMessage(
    chatId: KUUID,
    messageId: KUUID,
    userId: KUUID,
    evaluation: Boolean,
  ): Message {
    chatRepo.getMessage(chatId, messageId) ?: throw NotFoundException("Message not found")
    return chatRepo.evaluateMessage(messageId, userId, evaluation)
      ?: throw NotFoundException("Message not found")
  }

  fun getMessages(id: KUUID, userId: KUUID? = null): List<Message> {
    return if (userId != null) {
      chatRepo.getMessagesForUser(id, userId)
    } else {
      chatRepo.getMessages(id)
    }
  }

  fun countHistoryTokens(history: List<ChatMessage>, agentId: KUUID): Int {
    val agent =
      agentRepository.get(agentId)
        ?: throw apiError("Entity not found", "Agent with ID '$agentId' does not exist")
    val text = history.joinToString("\n") { it.content }
    return getEncoder(agent.model).encode(text).size()
  }

  private fun getEncoder(llm: String): Encoding {
    val registry = Encodings.newDefaultEncodingRegistry()
    for (type in ModelType.entries) {
      if (type.name == llm) {
        return registry.getEncodingForModel(type)
      }
    }
    throw apiError("Invalid model", "Cannot find tokenizer for model '$llm'")
  }

  fun embedQuery(query: String): List<Double> {
    // TODO: Implement
    return listOf()
  }
}
