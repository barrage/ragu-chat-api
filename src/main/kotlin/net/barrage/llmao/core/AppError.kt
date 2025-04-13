package net.barrage.llmao.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * The main application error structure for handling and sending errors to the client.
 *
 * If this is of type `Internal`, then this is usually a downstream service provider error.
 *
 * If this of type `API`, then it is guaranteed to be an error caused by receiving invalid input.
 */
@Serializable
open class AppError(
  /** Can be one of: API, Internal */
  val errorType: String,

  /** The reason for the error. Matched in the error handler to determine the HTTP status code. */
  val errorReason: ErrorReason,

  /** Optional error description. */
  val errorMessage: String? = null,

  /** The original caught error if this error originates from a caught exception. */
  @Transient val original: Throwable? = null,
) : Throwable(message = "${errorReason.reason}; $errorMessage", cause = original) {
  /**
   * Used to display an error message to the user. Used by custom chat agents to display user
   * friendly messages in chats.
   */
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
  /** Access token is missing or invalid. */
  Authentication("Unauthorized"),

  /**
   * Entity not found. Also thrown in cases where the user is not authorized to access the entity.
   */
  EntityDoesNotExist("Entity does not exist"),

  /** Conflicting entity is found. */
  EntityAlreadyExists("Entity already exists"),

  /** Invalid input value received from client. */
  InvalidParameter("Invalid parameter"),

  /** Depends on the context it was created in. Usually used to indicate an invalid state change. */
  InvalidOperation("Invalid operation"),

  /** Used for BLOBs. */
  PayloadTooLarge("Payload too large"),

  /** Downstream service error. */
  Internal("Something went wrong"),
}
