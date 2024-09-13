package net.barrage.llmao.dtos.chats

import kotlinx.serialization.Serializable
import net.barrage.llmao.dtos.llmconfigs.LLMConfigDTO
import net.barrage.llmao.dtos.llmconfigs.toLLMConfigDTO
import net.barrage.llmao.dtos.messages.MessageDTO
import net.barrage.llmao.dtos.messages.toMessageDTO
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.LLM_CONFIGS
import net.barrage.llmao.tables.references.MESSAGES
import org.jooq.Record

@Serializable
data class ChatDTO(
    val id: KUUID,
    val userId: KUUID,
    val agentId: Int,
    val title: String?,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
    val llmConfig: LLMConfigDTO,
    val messages: List<MessageDTO>
)

fun toChatDTO(chatData: List<Record>): ChatDTO {
    val llmConfig = chatData.first().into(LLM_CONFIGS).toLLMConfigDTO()
    val messages = chatData.filter { it[MESSAGES.ID] != null }.map { it.into(MESSAGES).toMessageDTO() }
    val chat = chatData.first().into(CHATS)

    return ChatDTO(
        id = chat.id!!,
        userId = chat.userId!!,
        agentId = chat.agentId!!,
        title = chat.title,
        createdAt = chat.createdAt!!,
        updatedAt = chat.updatedAt!!,
        llmConfig = llmConfig,
        messages = messages
    )
}