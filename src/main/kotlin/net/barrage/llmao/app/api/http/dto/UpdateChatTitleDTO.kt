package net.barrage.llmao.app.api.http.dto

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.Serializable

@Serializable
data class UpdateChatTitleDTO(var title: String) {
  fun validate(): ValidationResult {
    val errors: MutableList<String> = mutableListOf()

    if (title.length < 3) {
      errors.add("Chat title must be at least 3 characters long")
    }

    if (title.length > 255) {
      errors.add("Chat title is too long. Max 255 characters")
    }

    if (errors.isNotEmpty()) {
      return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
  }
}
