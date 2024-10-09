package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.utils.Logger
import net.barrage.llmao.utils.ValidationError

fun Application.configureErrorHandling() {
  install(StatusPages) {
    exception<AppError> { call, err -> call.respond(err.code(), err) }

    // Java exceptions

    exception<NoSuchElementException> { call, err ->
      call.respond(
        HttpStatusCode.NotFound,
        AppError.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
      )
    }

    exception<IllegalArgumentException> { call, err ->
      call.respond(
        HttpStatusCode.BadRequest,
        AppError.api(ErrorReason.InvalidParameter, err.message ?: err.localizedMessage),
      )
    }

    // KTOR specific exceptions

    exception<io.ktor.server.plugins.NotFoundException> { call, err ->
      call.respond(
        HttpStatusCode.NotFound,
        AppError.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
      )
    }

    exception<io.ktor.server.plugins.BadRequestException> { call, err ->
      if (err.cause is io.ktor.serialization.JsonConvertException) {
        call.handleJsonConvert(err.cause!! as io.ktor.serialization.JsonConvertException)
        return@exception
      }

      if (err.cause?.cause is io.ktor.serialization.JsonConvertException) {
        call.handleJsonConvert(err.cause!!.cause as io.ktor.serialization.JsonConvertException)
        return@exception
      }

      // TODO: Not good currently, we have to wait for QA to inject their dependencies
      // in our classes so we can see which errors we handle and how
      call.respond(HttpStatusCode.BadRequest, err)
    }

    exception<io.ktor.server.plugins.requestvalidation.RequestValidationException> { call, err ->
      val errors = err.reasons.map { Json.decodeFromString<ValidationError>(it) }
      call.respond(HttpStatusCode.UnprocessableEntity, errors)
    }

    exception<io.ktor.serialization.JsonConvertException> { call, err ->
      call.respond(
        HttpStatusCode.BadRequest,
        AppError.api(ErrorReason.InvalidParameter, err.localizedMessage),
      )
    }

    exception<Throwable> { call, err ->
      Logger.error("${err::class} | ${err.localizedMessage} | ${err.cause} | ${err.cause?.cause}")
      call.respond(HttpStatusCode.InternalServerError, err.localizedMessage)
    }
  }
}

private suspend fun ApplicationCall.handleJsonConvert(
  err: io.ktor.serialization.JsonConvertException
) {
  // In case of missing fields, only the message will be present
  val message = err.localizedMessage
  respond(HttpStatusCode.BadRequest, AppError.api(ErrorReason.InvalidParameter, message))
}
