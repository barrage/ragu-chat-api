package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.core.Email
import net.barrage.llmao.core.NotBlank
import net.barrage.llmao.core.Validation

@Serializable
data class User(
  val id: KUUID,
  @Email val email: String,
  @NotBlank val fullName: String,
  @NotBlank val firstName: String?,
  @NotBlank val lastName: String?,
  val active: Boolean,
  val role: Role,
  val avatar: String? = null,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
  val deletedAt: KOffsetDateTime? = null,
)

fun UsersRecord.toUser() =
  User(
    id = this.id!!,
    email = this.email,
    fullName = this.fullName,
    firstName = this.firstName,
    lastName = this.lastName,
    role = Role.valueOf(this.role),
    active = this.active!!,
    avatar = this.avatar,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    deletedAt = this.deletedAt,
  )

@Serializable
data class CreateUser(
  @Email val email: String,
  @NotBlank val fullName: String,
  @NotBlank val firstName: String,
  @NotBlank val lastName: String,
  val role: Role,
) : Validation

@Serializable
data class UpdateUser(
  @NotBlank val fullName: String? = null,
  @NotBlank val firstName: String? = null,
  @NotBlank val lastName: String? = null,
) : Validation

@Serializable
data class UpdateUserAdmin(
  @NotBlank val fullName: String? = null,
  @NotBlank val firstName: String? = null,
  @NotBlank val lastName: String? = null,
  @Email val email: String? = null,
  val role: Role? = null,
  val active: Boolean? = null,
) : Validation
