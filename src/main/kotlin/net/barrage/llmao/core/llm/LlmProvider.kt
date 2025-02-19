package net.barrage.llmao.core.llm

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
  fun id(): String

  /** Execute chat completion on an LLM. */
  suspend fun chatCompletion(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): ChatCompletion

  /** Create a stream that emits [ChatMessageChunk]s. */
  suspend fun completionStream(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): Flow<ChatMessageChunk>

  /** Return `true` if the implementor supports the model, `false` otherwise. */
  suspend fun supportsModel(model: String): Boolean

  suspend fun listModels(): List<String>
}
