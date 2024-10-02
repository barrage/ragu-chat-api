package net.barrage.llmao.core.models

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
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

@Serializable
data class UpdateUser(val fullName: String?, val firstName: String?, val lastName: String?) {
  fun validate(): ValidationResult {
    val rules =
      listOf(
        fullName?.let { validateNotEmpty(it, "fullName") },
        firstName?.let { validateNotEmpty(it, "firstName") },
        lastName?.let { validateNotEmpty(it, "lastName") },
      )

    val errors: List<String> = rules.filterNotNull()

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}

@Serializable
data class UpdateUserAdmin(
  val fullName: String?,
  val firstName: String?,
  val lastName: String?,
  val email: String?,
  val role: Role?,
) {
  fun validate(): ValidationResult {
    val rules =
      listOf(
        email?.let { validateEmail(email) },
        fullName?.let { validateNotEmpty(it, "fullName") },
        firstName?.let { validateNotEmpty(it, "firstName") },
        lastName?.let { validateNotEmpty(it, "lastName") },
      )

    val errors: List<String> = rules.filterNotNull()
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}

fun validateEmail(email: String): String? {
  return when {
    email.isBlank() -> "email must not be empty"
    !email.matches(Regex("^[\\w\\-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) ->
      "email must be a valid email address"
    else -> null
  }
}

fun validateNotEmpty(param: String, fieldName: String): String? {
  return when {
    param.isBlank() -> "$fieldName must not be empty"
    else -> null
  }
}
