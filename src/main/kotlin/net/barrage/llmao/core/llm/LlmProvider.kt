package net.barrage.llmao.core.llm

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
  fun id(): String

  /** Execute chat completion on an LLM. */
  suspend fun chatCompletion(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): ChatMessage

  /** Create a stream that emits [MessageChunk]s. */
  suspend fun completionStream(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): Flow<MessageChunk>

  /** Return `true` if the implementor supports the model, `false` otherwise. */
  suspend fun supportsModel(model: String): Boolean

  suspend fun listModels(): List<String>
}
