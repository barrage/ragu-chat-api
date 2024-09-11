package net.barrage.llmao.dtos.llmconfigs

import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.Languages
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.LlmConfigsRecord

@Serializable
data class LLMConfigDTO(
    val id: KUUID,
    val chatId: KUUID,
    val model: String,
    val streaming: Boolean,
    val temperature: Double,
    val language: Languages,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun LlmConfigsRecord.toLLMConfigDTO() = LLMConfigDTO(
    id = this.id!!,
    chatId = this.chatId!!,
    model = this.model!!,
    streaming = this.streaming!!,
    temperature = this.temperature!!,
    language = if (this.language.isNullOrBlank()) {
        Languages.CRO
    } else {
        Languages.valueOf(this.language!!)
    },
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!
)