package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.utils.Email
import net.barrage.llmao.utils.NotBlank
import net.barrage.llmao.utils.Validation

@Serializable
data class User(
  val id: KUUID,
  @Email val email: String,
  @NotBlank val fullName: String,
  @NotBlank val firstName: String?,
  @NotBlank val lastName: String?,
  val active: Boolean,
  val role: Role,
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
  @NotBlank val fullName: String?,
  @NotBlank val firstName: String?,
  @NotBlank val lastName: String?,
) : Validation

@Serializable
data class UpdateUserAdmin(
  @NotBlank val fullName: String?,
  @NotBlank val firstName: String?,
  @NotBlank val lastName: String?,
  @Email val email: String?,
  val role: Role?,
  val active: Boolean?,
) : Validation
