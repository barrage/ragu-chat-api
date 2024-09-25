package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.Roles

@Serializable
class NewDevUserDTO(
  val email: String,
  val firstName: String,
  val lastName: String,
  val role: Roles,
) {
  fun validate(): ValidationResult {
    val rules =
      listOf(validateEmail(email), validateFirstName(firstName), validateLastName(lastName))

    val errors: List<String> = rules.filterNotNull()

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}
