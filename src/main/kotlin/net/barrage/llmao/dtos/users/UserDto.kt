package net.barrage.llmao.dtos.users

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTimeSerializer
import net.barrage.llmao.serializers.KUUIDSerializer
import net.barrage.llmao.tables.records.UsersRecord
import java.time.OffsetDateTime
import java.util.*

@Serializable
class UserDto(
    @Serializable(with = KUUIDSerializer::class)
    val id: UUID,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val active: Boolean,
    val defaultAgentId: Int,
    @Serializable(with = KOffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = KOffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
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