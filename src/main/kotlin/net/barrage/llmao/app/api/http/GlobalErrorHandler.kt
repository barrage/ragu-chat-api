package net.barrage.llmao.app.api.http

import io.ktor.http.*
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.respond
import io.ktor.util.logging.*
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ValidationError

internal val LOG = KtorSimpleLogger("n.b.l.a.plugins.GlobalErrorHandler")

fun Application.configureErrorHandling() {
  install(StatusPages) {
    exception<AppError> { call, err ->
      LOG.error(err)
      call.respond(err.code(), err)
    }

    // Java exceptions

    exception<NoSuchElementException> { call, err ->
      LOG.error(err)
      call.respond(
        HttpStatusCode.NotFound,
        AppError.Companion.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
      )
    }

    exception<IllegalArgumentException> { call, err ->
      LOG.error(err)
      call.respond(
        HttpStatusCode.BadRequest,
        AppError.Companion.api(ErrorReason.InvalidParameter, err.message ?: err.localizedMessage),
      )
    }

    // KTOR specific exceptions

    exception<NotFoundException> { call, err ->
      LOG.error(err)
      call.respond(
        HttpStatusCode.NotFound,
        AppError.Companion.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
      )
    }

    exception<BadRequestException> { call, err ->
      LOG.error(err)
      if (err.cause is JsonConvertException) {
        call.handleJsonConvert(err.cause!! as JsonConvertException)
        return@exception
      }

      if (err.cause?.cause is JsonConvertException) {
        call.handleJsonConvert(err.cause!!.cause as JsonConvertException)
        return@exception
      }

      // TODO: Not good currently, we have to wait for QA to inject their dependencies
      // in our classes so we can see which errors we handle and how
      call.respond(HttpStatusCode.BadRequest, err)
    }

    exception<RequestValidationException> { call, err ->
      LOG.error(err)
      val errors = err.reasons.map { Json.decodeFromString<ValidationError>(it) }
      call.respond(HttpStatusCode.UnprocessableEntity, errors)
    }

    exception<JsonConvertException> { call, err ->
      LOG.error(err)
      call.respond(
        HttpStatusCode.BadRequest,
        AppError.Companion.api(ErrorReason.InvalidParameter, err.localizedMessage),
      )
    }

    exception<Exception> { call, err ->
      LOG.error(err)
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
