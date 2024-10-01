package net.barrage.llmao.core.llm

import net.barrage.llmao.models.Language

data class LlmConfig(
  /** LLM to use. */
  val model: String,

  /** How much milligrams of LSD the LLM consumes before generating the response */
  val temperature: Double = 0.1,

  /** Used by the prompt formatter to specify language directives to the LLM. */
  val language: Language = Language.CRO,
)
