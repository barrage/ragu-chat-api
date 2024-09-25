package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.UsersRecord

@Serializable
class User(
  val id: KUUID,
  val email: String,
  val fullName: String,
  val firstName: String?,
  val lastName: String?,
  val active: Boolean,
  val role: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun UsersRecord.toUser() =
  User(
    id = this.id!!,
    email = this.email!!,
    fullName = this.fullName!!,
    firstName = this.firstName,
    lastName = this.lastName,
    role = this.role!!,
    active = this.active!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
