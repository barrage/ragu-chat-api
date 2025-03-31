package net.barrage.llmao.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class AppError(
  val errorType: String,
  val errorReason: ErrorReason,
  val errorMessage: String? = null,
  @Transient val original: Throwable? = null,
) : Throwable(message = "${errorReason.reason}; $errorMessage", cause = original) {
  var displayMessage: String? = null

  companion object {
    fun api(
      reason: ErrorReason,
      description: String? = null,
      original: Throwable? = null,
    ): AppError {
      return AppError(
        errorType = "API",
        errorReason = reason,
        errorMessage = description,
        original = original,
      )
    }

    fun internal(message: String? = null, original: Throwable? = null): AppError {
      return AppError(
        errorType = "Internal",
        errorReason = ErrorReason.Internal,
        errorMessage = message,
        original = original,
      )
    }
  }

  fun withDisplayMessage(message: String): AppError {
    this.displayMessage = message
    return this
  }
}

@Serializable
enum class ErrorReason(val reason: String) {
  Authentication("Unauthorized"),
  CannotDeleteSelf("Cannot delete self"),
  CannotUpdateSelf("Cannot update self"),
  EntityDoesNotExist("Entity does not exist"),
  EntityAlreadyExists("Entity already exists"),
  InvalidParameter("Invalid parameter"),
  InvalidProvider("Unsupported provider"),
  VectorDatabase("Vector database error"),
  InvalidOperation("Invalid operation"),
  PayloadTooLarge("Payload too large"),

  // Websocket errors
  Websocket("Unprocessable message"),
  Internal("Something went wrong"),
}
