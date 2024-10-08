package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.utils.Logger
import net.barrage.llmao.utils.ValidationError

fun Application.configureErrorHandling() {
  install(StatusPages) {
    exception<Throwable> { call, err ->
      Logger.error("${err::class} | ${err.localizedMessage} | ${err.cause} | ${err.cause?.cause}")

      when (err) {
        is AppError -> {
          call.respond(err.code(), err)
        }

        is NoSuchElementException -> {
          call.respond(
            HttpStatusCode.NotFound,
            AppError.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
          )
        }

        is NotFoundException -> {
          call.respond(
            HttpStatusCode.NotFound,
            AppError.api(ErrorReason.EntityDoesNotExist, err.message ?: err.localizedMessage),
          )
        }

        is RequestValidationException -> {
          val errors = err.reasons.map { Json.decodeFromString<ValidationError>(it) }
          call.respond(HttpStatusCode.UnprocessableEntity, errors)
        }

        is IllegalArgumentException -> {
          call.respond(
            HttpStatusCode.BadRequest,
            AppError.api(ErrorReason.InvalidParameter, err.message ?: err.localizedMessage),
          )
        }

        is JsonConvertException -> {
          println(err)
          call.respond(
            HttpStatusCode.BadRequest,
            AppError.api(ErrorReason.InvalidParameter, err.localizedMessage),
          )
        }

        is BadRequestException -> {
          // err.cause is a BadRequestException

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

        else -> call.respond(HttpStatusCode.InternalServerError, AppError.internal())
      }
    }
  }
}

private suspend fun ApplicationCall.handleJsonConvert(err: JsonConvertException) {
  // In case of missing fields, only the message will be present
  val message = err.localizedMessage
  respond(HttpStatusCode.BadRequest, AppError.api(ErrorReason.InvalidParameter, message))
}
