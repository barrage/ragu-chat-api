package net.barrage.llmao.core.llm

import kotlinx.serialization.json.JsonObject

/** Used for basic configuration when prompting LLMs. */
data class ChatCompletionBaseParameters(
  /** Which LLM to use. */
  val model: String,

  /** How much milligrams of LSD the LLM consumes before generating the response */
  val temperature: Double = 0.1,

  /** The higher the value, the less likely the LLM will repeat already generated tokens. */
  val presencePenalty: Double? = null,

  /** Maximum number of tokens to generate on completions. */
  val maxTokens: Int? = null,
)

/** Used for more advanced configuration when prompting LLMs. */
data class ChatCompletionAgentParameters(
  /** Available tools to call and their handlers. */
  val tools: Tools? = null,
  /** JSON schema for structured responses from the LLM. */
  val responseFormat: ResponseFormat? = null,
)

data class ChatCompletionParameters(
  val base: ChatCompletionBaseParameters,
  val agent: ChatCompletionAgentParameters? = null,
)

/** Represents a structure for the LLM to respond in. */
data class ResponseFormat(
  val name: String? = null,
  val schema: JsonObject,
  val strict: Boolean = true,
)
