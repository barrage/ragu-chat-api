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
import net.barrage.llmao.core.models.ChatWithAgent
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.services.ChatService")

class ChatService(
  private val providers: ProviderState,
  private val chatRepository: ChatRepository,
  private val agentRepository: AgentRepository,
  private val userRepository: UserRepository,
) {
  fun listChatsAdmin(pagination: PaginationSort): CountedList<ChatWithUserAndAgent> {
    return chatRepository.getAllAdmin(pagination)
  }

  fun listChats(pagination: PaginationSort, userId: KUUID): CountedList<Chat> {
    return chatRepository.getAll(pagination, userId)
  }

  fun getChatWithAgent(id: KUUID, userId: KUUID): ChatWithAgent {
    val chat =
      chatRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    if (userId != chat.userId) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    val agent = agentRepository.get(chat.agentId)

    return ChatWithAgent(chat, agent.agent)
  }

  fun getChatWithUserAndAgent(id: KUUID): ChatWithUserAndAgent {
    val chat =
      chatRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent = agentRepository.get(chat.agentId)

    val user = userRepository.get(chat.userId)!!

    return ChatWithUserAndAgent(chat, user, agent.agent)
  }

  fun getChat(chatId: KUUID): ChatWithMessages? {
    return chatRepository.getWithMessages(chatId)
  }

  fun getMessages(chatId: KUUID): List<Message> {
    return chatRepository.getMessages(chatId)
  }

  fun storeChat(id: KUUID, userId: KUUID, agentId: KUUID, title: String?) {
    chatRepository.insert(id, userId, agentId, title)
  }

  suspend fun generateTitle(chatId: KUUID, prompt: String, agentId: KUUID): String {
    val agentFull = agentRepository.get(agentId)

    val titlePrompt = agentFull.configuration.agentInstructions.title(prompt)
    LOG.trace("Created title prompt:\n{}", titlePrompt)

    val llm = providers.llm.getProvider(agentFull.configuration.llmProvider)
    val title =
      llm
        .generateChatTitle(
          titlePrompt,
          LlmConfig(agentFull.configuration.model, agentFull.configuration.temperature),
        )
        .trim()

    LOG.trace("Title generated: {}", title)
    chatRepository.updateTitle(chatId, title) ?: throw NotFoundException("Chat not found")

    return title
  }

  fun updateTitle(chatId: KUUID, title: String) {
    chatRepository.updateTitle(chatId, title)
  }

  fun updateTitle(chatId: KUUID, userId: KUUID, title: String) {
    chatRepository.updateTitle(chatId, userId, title)
  }

  suspend fun chatCompletionStream(
    prompt: String,
    history: List<ChatMessage>,
    agentId: KUUID,
  ): Flow<List<TokenChunk>> {
    val agentFull = agentRepository.get(agentId)

    val vectorDb = providers.vector.getProvider(agentFull.agent.vectorProvider)
    val llm = providers.llm.getProvider(agentFull.configuration.llmProvider)

    val query = prepareChatPrompt(prompt, agentFull, history, vectorDb)

    return llm.completionStream(
      query,
      LlmConfig(agentFull.configuration.model, agentFull.configuration.temperature),
    )
  }

  suspend fun chatCompletion(prompt: String, history: List<ChatMessage>, agentId: KUUID): String {
    val agentFull = agentRepository.get(agentId)

    val vectorDb = providers.vector.getProvider(agentFull.agent.vectorProvider)
    val llm = providers.llm.getProvider(agentFull.configuration.llmProvider)

    val query = prepareChatPrompt(prompt, agentFull, history, vectorDb)

    return llm.chatCompletion(
      query,
      LlmConfig(agentFull.configuration.model, agentFull.configuration.temperature),
    )
  }

  fun processMessagePair(
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

  fun processFailedMessage(chatId: KUUID, userId: KUUID, prompt: String, reason: String) {
    chatRepository.insertFailedMessage(chatId, userId, prompt, reason)
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

  fun deleteChat(id: KUUID) {
    chatRepository.delete(id)
  }

  fun deleteChat(id: KUUID, userId: KUUID) {
    chatRepository.delete(id, userId)
  }

  fun evaluateMessage(chatId: KUUID, messageId: KUUID, evaluation: Boolean): Message {
    chatRepository.getMessage(chatId, messageId) ?: throw NotFoundException("Message not found")
    return chatRepository.evaluateMessage(messageId, evaluation)
      ?: throw NotFoundException("Message not found")
  }

  fun evaluateMessage(
    chatId: KUUID,
    messageId: KUUID,
    userId: KUUID,
    evaluation: Boolean,
  ): Message {
    chatRepository.getMessage(chatId, messageId) ?: throw NotFoundException("Message not found")
    return chatRepository.evaluateMessage(messageId, userId, evaluation)
      ?: throw NotFoundException("Message not found")
  }

  fun getMessages(id: KUUID, userId: KUUID? = null): List<Message> {
    return if (userId != null) {
      chatRepository.getMessagesForUser(id, userId)
    } else {
      chatRepository.getMessages(id)
    }
  }

  fun countHistoryTokens(history: List<ChatMessage>, agentId: KUUID): Int {
    val agentFull = agentRepository.get(agentId)
    val text = history.joinToString("\n") { it.content }
    return getEncoder(agentFull.configuration.model).encode(text).size()
  }

  private suspend fun prepareChatPrompt(
    prompt: String,
    agent: AgentFull,
    history: List<ChatMessage>,
    vectorDb: VectorDatabase,
  ): List<ChatMessage> {
    val systemMessage =
      systemMessage(
        "${agent.configuration.context}\n${agent.configuration.agentInstructions.language()}"
      )

    LOG.trace("Created system message {}", systemMessage)

    val embedded = embedQuery(agent.agent.embeddingProvider, agent.agent.embeddingModel, prompt)

    var documentation = ""
    for (collection in agent.collections) {
      val relatedChunks =
        vectorDb.query(embedded, collection.collection, collection.amount).joinToString("\n")
      val instruction = collection.instruction
      documentation += "$instruction\n$relatedChunks"
    }

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
