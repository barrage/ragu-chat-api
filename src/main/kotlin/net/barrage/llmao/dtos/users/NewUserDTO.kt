package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.Roles

@Serializable
class NewUserDTO(
  val email: String,
  val firstName: String,
  val lastName: String,
  val role: Roles,
  val defaultAgentId: Int,
) {
  fun validate(): ValidationResult {
    val rules =
      listOf(
        validateEmail(email),
        validateFirstName(firstName),
        validateLastName(lastName),
        validateDefaultAgentId(defaultAgentId),
      )

    val errors: List<String> = rules.filterNotNull()

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}
