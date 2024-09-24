package net.barrage.llmao.dtos.users

import kotlinx.serialization.Serializable
import net.barrage.llmao.dtos.PaginationInfo

@Serializable
data class PaginatedUserDTO (
    val users: List<UserDTO>,
    val pageInfo: PaginationInfo
)

fun toPaginatedUserDTO(users: List<UserDTO>, pageInfo: PaginationInfo): PaginatedUserDTO {
    return PaginatedUserDTO(
        users = users,
        pageInfo = pageInfo
    )
}