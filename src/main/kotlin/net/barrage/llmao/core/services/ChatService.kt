package net.barrage.llmao.core.services

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import io.ktor.server.plugins.*
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.services.ChatService")

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

  suspend fun generateTitle(chatId: KUUID, prompt: String, agentId: KUUID): String {
    val agentFull = agentRepository.get(agentId)

    val titlePrompt = agentFull.instructions.title(prompt, agentFull.agent.language)
    LOG.trace("Created title prompt:\n{}", titlePrompt)

    val llm = providers.llm.getProvider(agentFull.agent.llmProvider)
    val title =
      llm
        .generateChatTitle(
          titlePrompt,
          LlmConfig(agentFull.agent.model, agentFull.agent.temperature),
        )
        .trim()

    LOG.trace("Title generated: {}", title)
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
  ): Flow<List<TokenChunk>> {
    val agentFull = agentRepository.get(agentId)

    val vectorDb = providers.vector.getProvider(agentFull.agent.vectorProvider)
    val llm = providers.llm.getProvider(agentFull.agent.llmProvider)

    val query = prepareChatPrompt(prompt, agentFull, history, vectorDb)

    return llm.completionStream(
      query,
      LlmConfig(agentFull.agent.model, agentFull.agent.temperature),
    )
  }

  suspend fun chatCompletion(prompt: String, history: List<ChatMessage>, agentId: KUUID): String {
    val agentFull = agentRepository.get(agentId)

    val vectorDb = providers.vector.getProvider(agentFull.agent.vectorProvider)
    val llm = providers.llm.getProvider(agentFull.agent.llmProvider)

    val query = prepareChatPrompt(prompt, agentFull, history, vectorDb)

    return llm.chatCompletion(query, LlmConfig(agentFull.agent.model, agentFull.agent.temperature))
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

  suspend fun summarizeConversation(
    chatId: KUUID,
    history: List<ChatMessage>,
    agentId: KUUID,
  ): String {
    val agentFull = agentRepository.get(agentId)

    val llm = providers.llm.getProvider(agentFull.agent.llmProvider)

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

    val summaryPrompt = agentFull.instructions.summary(conversation, agentFull.agent.language)

    val summary =
      llm.summarizeConversation(
        summaryPrompt,
        LlmConfig(agentFull.agent.model, agentFull.agent.temperature),
      )

    chatRepo.insertSystemMessage(chatId, summary)

    return summary
  }

  private suspend fun prepareChatPrompt(
    prompt: String,
    agent: AgentFull,
    history: List<ChatMessage>,
    vectorDb: VectorDatabase,
  ): List<ChatMessage> {
    val systemMessage =
      systemMessage("${agent.agent.context}\n${agent.instructions.language(agent.agent.language)}")

    LOG.trace("Created system message {}", systemMessage)
    val embedded = embedQuery(agent.agent.embeddingProvider, agent.agent.embeddingModel, prompt)

    val queryOptions = agent.collections.map { Pair(it.collection, it.amount) }

    // TODO: For loop with instructions per collection
    val relatedChunks = vectorDb.query(embedded, queryOptions)

    val documentation = relatedChunks.joinToString("\n")

    val message = userMessage(prompt, documentation)
    LOG.trace("Created user message {}", message)

    val messages = mutableListOf(systemMessage, *history.toTypedArray(), message)

    return messages
  }

  private fun systemMessage(context: String): ChatMessage {
    return ChatMessage.system(context)
  }

  private fun userMessage(prompt: String, documentation: String): ChatMessage {
    val message =
      """
      Use the relevant information below, as well as the information from the current conversation to answer
      the prompt below. If there is enough information from the current conversation to answer the prompt,
      do so without referring to the relevant information. The user is not aware of the relevant information.
      Do not refer to the relevant information unless explicitly asked.
      
      Relevant information: ${"\"\"\""}
        $documentation
      ${"\"\"\""}
      
      Prompt: ${"\"\"\""}
        $prompt
      ${"\"\"\""}
    """
        .trimIndent()
    return ChatMessage.user(message)
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
    val agentFull = agentRepository.get(agentId)
    val text = history.joinToString("\n") { it.content }
    return getEncoder(agentFull.agent.model).encode(text).size()
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

  private suspend fun embedQuery(provider: String, model: String, input: String): List<Double> {
    val embedder = providers.embedding.getProvider(provider)
    return embedder.embed(input, model)
  }
}
