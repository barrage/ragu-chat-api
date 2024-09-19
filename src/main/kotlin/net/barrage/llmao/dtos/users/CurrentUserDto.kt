package net.barrage.llmao.dtos.users

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID

@Serializable
class CurrentUserDto(
    val id: KUUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val active: Boolean,
    val defaultAgentId: Int,
    val sessionId: KUUID,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun toCurrentUserDto(user: UserDto, sessionId: KUUID): CurrentUserDto {
    return CurrentUserDto(
        id = user.id,
        email = user.email,
        firstName = user.firstName,
        lastName = user.lastName,
        role = user.role,
        active = user.active,
        defaultAgentId = user.defaultAgentId,
        sessionId = sessionId,
        createdAt = user.createdAt,
        updatedAt = user.updatedAt
    )
}