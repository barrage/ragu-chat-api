package net.barrage.llmao.core.services

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import io.ktor.server.plugins.*
import net.barrage.llmao.error.apiError
import net.barrage.llmao.llm.PromptFormatter
import net.barrage.llmao.llm.conversation.ConversationLlm
import net.barrage.llmao.llm.types.ChatMessage
import net.barrage.llmao.llm.types.LlmConfig
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.ChatWithConfig
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.VectorQueryOptions
import net.barrage.llmao.repositories.ChatRepository
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.weaviate.Weaver

class ChatService(private val chatRepo: ChatRepository, private val vectorDb: Weaver) {
  fun listChats(): List<Chat> {
    return chatRepo.getAll()
  }

  fun listChats(userId: KUUID): List<Chat> {
    return chatRepo.getAllForUser(userId)
  }

  fun getChat(chatId: KUUID): ChatWithConfig {
    return chatRepo.getChatWithConfig(chatId)
  }

  fun getMessages(chatId: KUUID): List<Message> {
    return chatRepo.getMessages(chatId)
  }

  fun storeChat(id: KUUID, userId: KUUID, agentId: Int, llmConfig: LlmConfig, title: String?) {
    chatRepo.insertWithConfig(
      id,
      userId,
      agentId,
      title,
      llmConfig.model,
      llmConfig.temperature,
      llmConfig.language.language,
      llmConfig.provider,
    )
  }

  suspend fun generateTitle(
    chatId: KUUID,
    prompt: String,
    formatter: PromptFormatter,
    llm: ConversationLlm,
    llmConfig: LlmConfig,
  ): String {
    val titlePrompt = formatter.title(prompt)
    val title = llm.generateChatTitle(titlePrompt, llmConfig).trim()
    chatRepo.updateTitle(chatId, title) ?: throw NotFoundException("Chat not found")
    return title
  }

  fun updateTitle(chatId: KUUID, title: String) {
    chatRepo.updateTitle(chatId, title)
  }

  fun updateTitle(chatId: KUUID, userId: KUUID, title: String) {
    chatRepo.updateTitle(chatId, userId, title)
  }

  suspend fun chatCompletion(
    prompt: String,
    history: List<ChatMessage>,
    formatter: PromptFormatter,
    vectorOptions: VectorQueryOptions,
    llm: ConversationLlm,
    llmConfig: LlmConfig,
  ): String {
    val query = prepareChatPrompt(prompt, history, formatter, vectorOptions)
    return llm.chatCompletion(query, llmConfig).trim()
  }

  fun processMessagePair(
    chatId: KUUID,
    userId: KUUID,
    agentId: Int,
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

  fun prepareChatPrompt(
    prompt: String,
    history: List<ChatMessage>,
    formatter: PromptFormatter,
    queryOptions: VectorQueryOptions,
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
    llm: ConversationLlm,
    llmConfig: LlmConfig,
  ): String {

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

    val summary = llm.summarizeConversation(summaryPrompt, config = llmConfig).trim()

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

  fun countHistoryTokens(history: List<ChatMessage>, llm: String): Int {
    val text = history.joinToString("\n") { it.content }
    return getEncoder(llm).encode(text).size()
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
