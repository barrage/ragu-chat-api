package net.barrage.llmao.core.model

import kotlinx.serialization.Serializable
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
