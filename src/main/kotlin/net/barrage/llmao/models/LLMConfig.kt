package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.LlmConfigsRecord

@Serializable
class LLMConfig(
    val id: KUUID,
    val chatId: KUUID,
    val model: LLMModels,
    val streaming: Boolean,
    val temperature: Double,
    val language: String,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun LlmConfigsRecord.toLLMConfig() = LLMConfig(
    id = this.id!!,
    chatId = this.chatId!!,
    model = LLMModels.valueOf(this.model!!),
    streaming = this.streaming!!,
    temperature = this.temperature!!,
    language = this.language!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!
)