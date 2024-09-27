package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.models.Role

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
