package net.barrage.llmao.core.llm

data class LlmConfig(
  /** LLM to use. */
  val model: String,

  /** How many milligrams of LSD the LLM consumes before generating the response */
  val temperature: Double = 0.1,

  /** Maximum number of tokens to generate. */
  val maxCompletionTokens: Int? = null,

  /** Higher values increase likelihood of discussing new topics by penalizing repeated tokens. */
  val presencePenalty: Double = 0.0,
)
