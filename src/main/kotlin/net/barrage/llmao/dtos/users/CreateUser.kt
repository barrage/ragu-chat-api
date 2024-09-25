package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.Role

@Serializable
class CreateUser(
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
