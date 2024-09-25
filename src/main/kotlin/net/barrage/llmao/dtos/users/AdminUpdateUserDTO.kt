package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable

@Serializable
class AdminUpdateUserDTO(
  val email: String,
  override val firstName: String,
  override val lastName: String,
  override val defaultAgentId: Int,
) : UpdateUser {
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
