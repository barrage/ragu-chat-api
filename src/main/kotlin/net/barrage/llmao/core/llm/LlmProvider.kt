package net.barrage.llmao.core.llm

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
  fun id(): String

  /** Execute chat completion on an LLM. */
  suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String

  /** Create a stream that emits [TokenChunk]s. */
  suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>>

  /** Return `true` if the implementor supports the model, `false` otherwise. */
  suspend fun supportsModel(model: String): Boolean

  suspend fun listModels(): List<String>
}
