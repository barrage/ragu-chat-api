package net.barrage.llmao.core.llm

import kotlinx.coroutines.flow.Flow

interface ConversationLlm {
  fun id(): String

  /** Execute chat completion on an LLM. */
  suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String

  /** Create a stream that emits [TokenChunk]s. */
  suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>>

  /**
   * Create a title. The [proompt] is expected to be the full instructions on how to generate it.
   */
  suspend fun generateChatTitle(proompt: String, config: LlmConfig): String

  /**
   * Summarize a conversation. The [proompt] is expected to be the full message history concatenated
   * into a single string.
   */
  suspend fun summarizeConversation(
    proompt: String,
    config: LlmConfig,
    maxTokens: Int? = 1000,
  ): String

  /** Return `true` if the implementor supports the model, `false` otherwise. */
  suspend fun supportsModel(model: String): Boolean

  suspend fun listModels(): List<String>
}
