package net.barrage.llmao.llm.conversation

import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.llm.types.ChatMessage
import net.barrage.llmao.llm.types.LLMConversationConfig
import net.barrage.llmao.llm.types.TokenChunk

interface ConversationLlm {
  suspend fun chatCompletion(messages: List<ChatMessage>): String

  suspend fun completionStream(messages: List<ChatMessage>): Flow<List<TokenChunk>>

  suspend fun generateChatTitle(proompt: String): String

  suspend fun summarizeConversation(proompt: String, maxTokens: Int? = 1000): String

  fun config(): LLMConversationConfig
}
