package net.barrage.llmao.core.llm

import com.aallam.openai.api.core.FinishReason

data class TokenChunk(
  val id: String,
  val created: Int,
  val content: String? = null,
  val stopReason: FinishReason? = null,
)
