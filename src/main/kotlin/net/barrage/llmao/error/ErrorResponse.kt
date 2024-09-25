package net.barrage.llmao.error

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(private val errors: List<Error> = emptyList()) {
  fun setErrors(errors: List<Error>): ErrorResponse {
    return ErrorResponse(errors)
  }

  fun getErrors(): List<Error> {
    return errors.toList()
  }
}
