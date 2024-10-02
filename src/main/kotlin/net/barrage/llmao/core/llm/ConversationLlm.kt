package net.barrage.llmao.core.llm

import kotlinx.coroutines.flow.Flow

interface ConversationLlm {
  fun id(): String

  suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String

  suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>>

  suspend fun generateChatTitle(proompt: String, config: LlmConfig): String

  suspend fun summarizeConversation(
    proompt: String,
    config: LlmConfig,
    maxTokens: Int? = 1000,
  ): String

  fun supportsModel(model: String): Boolean
}
