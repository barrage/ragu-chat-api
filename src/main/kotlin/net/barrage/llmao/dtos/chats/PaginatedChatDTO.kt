package net.barrage.llmao.dtos.chats

import kotlinx.serialization.Serializable
import net.barrage.llmao.dtos.PaginationInfo

@Serializable
data class PaginatedChatDTO (
    val chats: List<ChatDTO>,
    val pageInfo: PaginationInfo
)

fun toPaginatedChatDTO(chats: List<ChatDTO>, pageInfo: PaginationInfo): PaginatedChatDTO {
    return PaginatedChatDTO(
        chats = chats,
        pageInfo = pageInfo
    )
}