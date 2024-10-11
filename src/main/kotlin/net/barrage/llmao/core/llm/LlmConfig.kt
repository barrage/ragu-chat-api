package net.barrage.llmao.core.llm

data class LlmConfig(
  /** LLM to use. */
  val model: String,

  /** How much milligrams of LSD the LLM consumes before generating the response */
  val temperature: Double = 0.1,
)
