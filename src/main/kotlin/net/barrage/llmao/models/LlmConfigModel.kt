package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.LlmConfigsRecord

@Serializable
class LlmConfigModel(
  val id: KUUID,
  val chatId: KUUID,
  val model: String,
  val provider: String,
  val temperature: Double,
  val language: Language,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun LlmConfigsRecord.toLlmConfig() =
  LlmConfigModel(
    id = this.id!!,
    chatId = this.chatId!!,
    model = this.model!!,
    provider = this.provider!!,
    temperature = this.temperature!!,
    language = Language.tryFromString(this.language!!),
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
