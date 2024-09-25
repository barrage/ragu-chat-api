package net.barrage.llmao.dtos.agents

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable

@Serializable
data class NewAgentDTO(val name: String, val context: String) {
  fun validate(): ValidationResult {
    val errors: MutableList<String> = mutableListOf()

    if (name.length < 3) {
      errors.add("Agent name must be at least 3 characters long")
    }

    if (name.length > 255) {
      errors.add("Agent name is too long. Max 255 characters")
    }

    if (context.length < 20) {
      errors.add("Agent context must be at least 20 characters long")
    }

    if (errors.isNotEmpty()) {
      return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
  }
}
