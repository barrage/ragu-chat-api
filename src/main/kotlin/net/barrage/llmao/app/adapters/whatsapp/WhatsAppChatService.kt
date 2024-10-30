package net.barrage.llmao.app.adapters.whatsapp

import io.ktor.util.logging.*
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgentFull
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.repositories.WhatsAppRepository
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.VectorDatabase

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.adapters.whatsapp.WhatsAppChatService")

class WhatsAppChatService(
  private val providers: ProviderState,
  private val repository: WhatsAppRepository,
) {
  fun getChat(userId: KUUID): WhatsAppChat {
    val chat = repository.getChatByUserId(userId)
    if (chat == null) {
      val id = KUUID.randomUUID()
      return repository.storeChat(id, userId)
    }
    LOG.trace("Found chat {}", chat)
    return chat
  }

  fun getMessages(chatId: KUUID, limit: Int? = null): List<WhatsAppMessage> {
    return repository.getMessages(chatId, limit)
  }

  suspend fun chatCompletion(prompt: String, history: List<ChatMessage>, agentId: KUUID): String {
    val agentFull = repository.getAgent(agentId)

    val vectorDb = providers.vector.getProvider(agentFull.agent.vectorProvider)
    val llm = providers.llm.getProvider(agentFull.agent.llmProvider)

    val query = prepareChatPrompt(prompt, agentFull, history, vectorDb)

    return llm.chatCompletion(query, LlmConfig(agentFull.agent.model, agentFull.agent.temperature))
  }

  private suspend fun prepareChatPrompt(
    prompt: String,
    agentFull: WhatsAppAgentFull,
    history: List<ChatMessage>,
    vectorDb: VectorDatabase,
  ): List<ChatMessage> {
    val systemMessage = systemMessage("${agentFull.agent.context}\n${agentFull.agent.language()}")

    LOG.trace("Created system message {}", systemMessage)

    val embedded =
      embedQuery(agentFull.agent.embeddingProvider, agentFull.agent.embeddingModel, prompt)

    var collectionInstructions = ""

    val collections = agentFull.collections.map { Pair(it.collection, it.amount) }.toList()
    val relatedChunks = vectorDb.query(embedded, collections)

    for (collection in agentFull.collections) {
      val instruction = collection.instruction
      val collectionData = relatedChunks[collection.collection]?.joinToString("\n") { it.content }

      collectionData?.let {
        collectionInstructions += "$instruction\n\"\"\n\t$collectionData\n\"\""
      }
    }

    val message = userMessage(prompt, collectionInstructions)

    LOG.trace("Created user message {}", message)

    val messages = mutableListOf(systemMessage, *history.toTypedArray(), message)

    return messages
  }

  private fun systemMessage(context: String): ChatMessage {
    return ChatMessage.system(context)
  }

  private suspend fun embedQuery(provider: String, model: String, input: String): List<Double> {
    val embedder = providers.embedding.getProvider(provider)
    return embedder.embed(input, model)
  }

  private fun userMessage(prompt: String, documentation: String): ChatMessage {
    val message =
      """
      Use the relevant information below, as well as the information from the current conversation to answer
      the prompt below. If there is enough information from the current conversation to answer the prompt,
      do so without referring to the relevant information. The user is not aware of the relevant information.
      Do not refer to the relevant information unless explicitly asked. Provide concise and to-the-point answers,
      as this is for WhatsApp where screen space is limited.
      Aim for brevity while maintaining clarity and completeness.
      
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

  fun storeMessages(
    chatId: KUUID,
    userId: KUUID,
    agentId: KUUID,
    proompt: String,
    response: String,
  ) {
    val userMessage = repository.insertUserMessage(chatId, userId, proompt)
    repository.insertAssistantMessage(chatId, agentId, userMessage.id, response)
  }
}
