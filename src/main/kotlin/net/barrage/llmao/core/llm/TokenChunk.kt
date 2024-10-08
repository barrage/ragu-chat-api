package net.barrage.llmao.core.llm

import com.aallam.openai.api.core.FinishReason
import kotlinx.serialization.Serializable

@Serializable
data class TokenChunk(
  val id: String,
  val created: Long,
  val content: String? = null,
  val stopReason: FinishReason? = null,
)
