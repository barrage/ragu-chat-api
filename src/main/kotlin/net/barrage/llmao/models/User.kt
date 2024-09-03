package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTimeSerializer
import net.barrage.llmao.serializers.KUUIDSerializer
import net.barrage.llmao.tables.records.UsersRecord
import java.time.OffsetDateTime
import java.util.*

@Serializable
class User(
    @Serializable(with = KUUIDSerializer::class)
    val id: UUID,
    val username: String,
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val active: Boolean,
    val role: String,
    val defaultAgentId: Int,
    @Serializable(with = KOffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = KOffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
)

fun UsersRecord.toUser() = User(
    id = this.id!!,
    username = this.username!!,
    email = this.email!!,
    password = this.password!!,
    firstName = this.firstName!!,
    lastName = this.lastName!!,
    role = this.role!!,
    defaultAgentId = this.defaultAgentId!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    active = this.active!!,
)