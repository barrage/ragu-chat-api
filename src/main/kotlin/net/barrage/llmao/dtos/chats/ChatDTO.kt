package net.barrage.llmao.dtos.chats

import kotlinx.serialization.Serializable
import net.barrage.llmao.models.LlmConfigModel
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID

@Serializable
data class ChatDTO(
  val id: KUUID,
  val userId: KUUID,
  val agentId: Int,
  val title: String?,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
  val llmConfig: LlmConfigModel,
)
