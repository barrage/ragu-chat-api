package net.barrage.llmao.core.llm

/** Data class used for prompting LLMs. */
data class ChatCompletionParameters(
  /** Which LLM to use. */
  val model: String,

  /** How much milligrams of LSD the LLM consumes before generating the response */
  val temperature: Double = 0.1,

  /** The higher the value, the less likely the LLM will repeat already generated tokens. */
  val presencePenalty: Double,

  /** Maximum number of tokens to generate on completions. */
  val maxTokens: Int?,

  /** Available tools to call. */
  val tools: List<ToolDefinition>? = null,
)
