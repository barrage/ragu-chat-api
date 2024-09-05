package net.barrage.llmao.dtos.users

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.UsersRecord

@Serializable
class UserDto(
    val id: KUUID,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val active: Boolean,
    val defaultAgentId: Int,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun UsersRecord.toUserDto() = UserDto(
    id = this.id!!,
    username = this.username!!,
    email = this.email!!,
    firstName = this.firstName!!,
    lastName = this.lastName!!,
    role = this.role!!,
    defaultAgentId = this.defaultAgentId!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    active = this.active!!,
)