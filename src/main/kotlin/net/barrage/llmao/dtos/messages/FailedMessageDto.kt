package net.barrage.llmao.dtos.messages

import com.aallam.openai.api.core.FinishReason
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.FailedMessagesRecord

class FailedMessageDto (
    val id: KUUID,
    val failReason: FinishReason,
    val userId: KUUID,
    val content: String,
    val chatId: KUUID,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun FailedMessagesRecord.toFailedMessageDto(): FailedMessageDto {
    return FailedMessageDto(
        id = this.id!!,
        failReason = FinishReason(this.failReason!!),
        userId = this.userId!!,
        content = this.content!!,
        chatId = this.chatId!!,
        createdAt = this.createdAt!!,
        updatedAt = this.updatedAt!!,
    )
}
