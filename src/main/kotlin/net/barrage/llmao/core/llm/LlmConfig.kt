package net.barrage.llmao.core.llm

/** Ephemeral data class used for prompting LLMs. */
data class LlmConfig(
  /** LLM to use. */
  val model: String,

  /** How much milligrams of LSD the LLM consumes before generating the response */
  val temperature: Double = 0.1,

  /** Maximum number of tokens to generate. */
  val maxTokens: Int? = null,

  /** Available tools to call. */
  val tools: List<ToolDefinition>? = null,
)
