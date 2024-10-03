package net.barrage.llmao.error

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(private val errors: List<AppError> = emptyList()) {
  fun setErrors(errors: List<AppError>): ErrorResponse {
    return ErrorResponse(errors)
  }

  fun getErrors(): List<AppError> {
    return errors.toList()
  }
}
