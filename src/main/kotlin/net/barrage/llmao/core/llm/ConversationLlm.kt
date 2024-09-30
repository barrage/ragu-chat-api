package net.barrage.llmao.core.llm

import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.core.chat.ChatMessage

interface ConversationLlm {
  suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String

  suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>>

  suspend fun generateChatTitle(proompt: String, config: LlmConfig): String

  suspend fun summarizeConversation(
    proompt: String,
    maxTokens: Int? = 1000,
    config: LlmConfig,
  ): String

  fun supportsModel(model: String): Boolean
}
