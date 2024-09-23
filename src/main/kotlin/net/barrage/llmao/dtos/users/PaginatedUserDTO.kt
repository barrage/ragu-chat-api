package net.barrage.llmao.dtos.users

import kotlinx.serialization.Serializable
import net.barrage.llmao.dtos.PaginationInfo

@Serializable
data class PaginatedUserDTO (
    val users: List<UserDto>,
    val pageInfo: PaginationInfo
)

fun toPaginatedUserDTO(users: List<UserDto>, pageInfo: PaginationInfo): PaginatedUserDTO {
    return PaginatedUserDTO(
        users = users,
        pageInfo = pageInfo
    )
}