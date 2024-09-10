package net.barrage.llmao.llm.conversation

import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.enums.Languages
import net.barrage.llmao.llm.ChatMessage

interface ConversationLlm {
    suspend fun chatCompletion(messages: List<ChatMessage>): String

    suspend fun completionStream(messages: List<ChatMessage>): Flow<List<TokenChunk>>

    suspend fun generateChatTitle(proompt: String): String

    suspend fun summarizeConversation(proompt: String, maxTokens: Int): String

    fun config(): LLMConversationConfig
}

data class LLMConversationConfig(
    val temperature: Double,
    val model: LLMModels,
    val language: Languages,
)

data class TokenChunk(
    val id: String,
    val created: Int,
    val content: String? = null,
    val stopReason: String? = null
)
