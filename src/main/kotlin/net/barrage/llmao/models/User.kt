package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.UsersRecord

@Serializable
class User(
  val id: KUUID,
  val email: String,
  val firstName: String,
  val lastName: String,
  val active: Boolean,
  val role: String,
  val defaultAgentId: Int,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun UsersRecord.toUser() =
  User(
    id = this.id!!,
    email = this.email!!,
    firstName = this.firstName!!,
    lastName = this.lastName!!,
    role = this.role!!,
    defaultAgentId = this.defaultAgentId!!,
    active = this.active!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
