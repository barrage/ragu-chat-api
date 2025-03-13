package net.barrage.llmao.core.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID

@Serializable
data class SpecialistWorkflow(
  val id: KUUID,
  val userId: String,
  val type: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable
data class SpecialistMessageGroup(
  val id: KUUID,
  val workflowId: KUUID,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable
data class SpecialistMessage(
  val id: KUUID,
  val order: Int,
  val messageGroupId: KUUID,
  val senderType: String,
  val content: String?,
  val finishReason: String?,
  val toolCalls: String?,
  val toolCallId: String?,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable
data class SpecialistMessageInsert(
  val senderType: String,
  val content: String?,
  val toolCalls: String?,
  val toolCallId: String?,
  val finishReason: FinishReason?,
)
