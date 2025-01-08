package net.barrage.llmao.error

import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
open class AppError(val type: String, val reason: ErrorReason, val description: String? = null) :
  Throwable() {
  companion object {
    fun api(reason: ErrorReason, description: String? = null): AppError {
      return AppError("API", reason, description)
    }

    fun internal(): AppError {
      return AppError("Internal", ErrorReason.Internal)
    }

    fun internal(message: String): AppError {
      return AppError("Internal", ErrorReason.Internal, message)
    }
  }

  fun code(): HttpStatusCode {
    return when (reason) {
      ErrorReason.Authentication -> HttpStatusCode.Unauthorized
      ErrorReason.CannotDeleteSelf -> HttpStatusCode.Conflict
      ErrorReason.CannotUpdateSelf -> HttpStatusCode.Conflict
      ErrorReason.EntityDoesNotExist -> HttpStatusCode.NotFound
      ErrorReason.EntityAlreadyExists -> HttpStatusCode.Conflict
      ErrorReason.InvalidProvider -> HttpStatusCode.BadRequest
      ErrorReason.InvalidParameter -> HttpStatusCode.BadRequest
      ErrorReason.InvalidOperation -> HttpStatusCode.BadRequest
      ErrorReason.PayloadTooLarge -> HttpStatusCode.PayloadTooLarge
      ErrorReason.InvalidContentType -> HttpStatusCode.BadRequest
      else -> HttpStatusCode.InternalServerError
    }
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
  InvalidContentType("Invalid content type"),

  // Websocket errors
  Websocket("Unprocessable message"),
  Internal("Something went wrong"),
}
