package net.barrage.llmao.core.http

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.error
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ValidationError

private val log = KtorSimpleLogger("net.barrage.llmao.core.http.GlobalErrorHandler")

fun Application.configureErrorHandling() {
  log.info("Configuring error handling")

  install(StatusPages) {
    exception<AppError> { call, err ->
      log.error(err)
      call.respond(err.code(), err)
    }

    // Java exceptions

    exception<NoSuchElementException> { call, err ->
      log.error(err)
      call.respond(
        HttpStatusCode.NotFound,
        AppError.Companion.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
      )
    }

    exception<IllegalArgumentException> { call, err ->
      log.error(err)
      call.respond(
        HttpStatusCode.BadRequest,
        AppError.Companion.api(ErrorReason.InvalidParameter, err.message ?: err.localizedMessage),
      )
    }

    // KTOR specific exceptions

    exception<NotFoundException> { call, err ->
      log.error(err)
      call.respond(
        HttpStatusCode.NotFound,
        AppError.Companion.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
      )
    }

    exception<BadRequestException> { call, err ->
      log.error(err)
      if (err.cause is JsonConvertException) {
        call.handleJsonConvert(err.cause!! as JsonConvertException)
        return@exception
      }

      if (err.cause?.cause is JsonConvertException) {
        call.handleJsonConvert(err.cause!!.cause as JsonConvertException)
        return@exception
      }

      call.respond(HttpStatusCode.BadRequest, err)
    }

    exception<RequestValidationException> { call, err ->
      log.error(err)
      val errors = err.reasons.map { Json.decodeFromString<ValidationError>(it) }
      call.respond(HttpStatusCode.UnprocessableEntity, errors)
    }

    exception<JsonConvertException> { call, err ->
      log.error(err)
      call.respond(
        HttpStatusCode.BadRequest,
        AppError.Companion.api(ErrorReason.InvalidParameter, err.localizedMessage),
      )
    }

    exception<Exception> { call, err ->
      log.error(err)
      call.respond(
        HttpStatusCode.InternalServerError,
        err.message ?: "An unexpected error occurred.",
      )
    }
  }
}

private suspend fun ApplicationCall.handleJsonConvert(err: JsonConvertException) {
  // In case of missing fields, only the message will be present
  val message = err.localizedMessage
  respond(HttpStatusCode.BadRequest, AppError.Companion.api(ErrorReason.InvalidParameter, message))
}

fun AppError.code(): HttpStatusCode {
  return when (errorReason) {
    ErrorReason.Authentication -> HttpStatusCode.Unauthorized
    ErrorReason.EntityDoesNotExist -> HttpStatusCode.NotFound
    ErrorReason.EntityAlreadyExists -> HttpStatusCode.Conflict
    ErrorReason.InvalidParameter,
    ErrorReason.InvalidOperation -> HttpStatusCode.BadRequest

    ErrorReason.PayloadTooLarge -> HttpStatusCode.PayloadTooLarge
    ErrorReason.Internal -> HttpStatusCode.InternalServerError
  }
}
