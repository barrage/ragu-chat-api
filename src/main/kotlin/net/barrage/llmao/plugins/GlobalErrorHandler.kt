package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import net.barrage.llmao.error.Error
import net.barrage.llmao.error.apiError
import net.barrage.llmao.error.internalError

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureErrorHandling() {
  install(StatusPages) {
    exception<Throwable> { call, throwable ->
      var statusCode: HttpStatusCode = HttpStatusCode.InternalServerError
      val errors = mutableListOf<Error>()

      when (throwable) {
        is NoSuchElementException -> {
          statusCode = HttpStatusCode.NotFound
          errors.add(apiError("Not found", throwable.message ?: throwable.localizedMessage))
        }

        is NotFoundException -> {
          statusCode = HttpStatusCode.NotFound
          errors.add(apiError("Not found", throwable.message ?: throwable.localizedMessage))
        }

        is RequestValidationException -> {
          statusCode = HttpStatusCode.UnprocessableEntity
          throwable.reasons.forEach { errors.add(apiError("Validation error", it)) }
        }

        is IllegalArgumentException -> {
          statusCode = HttpStatusCode.BadRequest
          errors.add(apiError("Bad request", throwable.message ?: throwable.localizedMessage))
        }

        // handles request body and query parsing exceptions
        is BadRequestException -> {
          statusCode = HttpStatusCode.BadRequest

          if (throwable.cause != null) {
            val cause = throwable.cause!!

            if (cause is JsonConvertException) {
              // handles body parsing exceptions
              statusCode = HttpStatusCode.UnprocessableEntity

              if (cause.cause is MissingFieldException) {
                (cause.cause as MissingFieldException).missingFields.forEach {
                  errors.add(apiError("Missing field", "Missing required field: $it"))
                }
              } else if (cause.localizedMessage.startsWith("Illegal input")) {
                cause.printStackTrace()
                if (cause.localizedMessage.contains("UUID")) {
                  errors.add(
                    apiError(
                      "Illegal input",
                      cause.localizedMessage.split(": ").subList(1, 3).joinToString(": "),
                    )
                  )
                } else
                  errors.add(
                    apiError(
                      "Illegal input",
                      cause.localizedMessage.split("\n")[0].split(": ").subList(2, 4).joinToString {
                        it
                      },
                    )
                  )
              } else if (
                throwable.localizedMessage.startsWith("Failed to convert request body to class")
              ) {
                val className = throwable.localizedMessage.split(" ")[7]
                val clazz = Class.forName(className).kotlin as KClass<*>
                errors.add(
                  apiError("Bad request", "Expected request body: " + printClassDefinition(clazz))
                )
              } else {
                errors.add(apiError("Bad request", cause.message ?: cause.localizedMessage))
              }
            } else if (cause is NumberFormatException) {
              val numberClass: String = cause.stackTrace[2].className.substring(10)
              val regex = Regex("\"(.*?)\"")
              val input = regex.find(cause.localizedMessage)!!.groups.first()!!.value
              errors.add(
                apiError("Bad request", "Invalid number format ($numberClass) for input $input")
              )
            } else if (cause is IllegalArgumentException) {
              errors.add(apiError("Bad request", throwable.cause!!.localizedMessage))
            } else {
              throwable.printStackTrace()
              errors.add(apiError("Bad request", cause.message ?: cause.localizedMessage))
            }
          } else {
            throwable.printStackTrace()
            errors.add(apiError("Bad request", throwable.message ?: throwable.localizedMessage))
          }
        }

        else -> {
          errors.add(internalError())
        }
      }

      call.respond(statusCode, errors)
    }
  }
}

fun printClassDefinition(clazz: KClass<*>): String {
  return clazz.memberProperties.joinToString(", \n\t", "{\n\t", "\n}") {
    "${it.name}: ${it.returnType.jvmErasure.simpleName}"
  }
}
