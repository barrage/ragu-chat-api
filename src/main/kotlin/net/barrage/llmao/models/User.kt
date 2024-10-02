package net.barrage.llmao.models

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.dtos.users.validateEmail
import net.barrage.llmao.dtos.users.validateNotEmpty
import net.barrage.llmao.tables.records.UsersRecord

@Serializable
data class User(
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

@Serializable
data class CreateUser(
  val email: String,
  val fullName: String,
  val firstName: String,
  val lastName: String,
  val role: Role,
) {
  fun validate(): ValidationResult {
    val rules =
      listOf(
        validateEmail(email),
        validateNotEmpty(firstName, "firstName"),
        validateNotEmpty(lastName, "lastName"),
      )

    val errors: List<String> = rules.filterNotNull()

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}
