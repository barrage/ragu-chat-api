package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable

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
