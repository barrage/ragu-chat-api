package net.barrage.llmao.core

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.http.pathUuid
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.test.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppErrorHandlingTests : IntegrationTest() {

  @Serializable data class TestJson(val foo: String, val bar: Int)

  @Test
  fun invalidHttpMethod() = test {
    routing { put("/error-test") { call.receive<TestJson>() } }
    val response = client.post("/error-test")
    assertEquals(405, response.status.value)
  }

  @Test
  fun invalidJsonFieldType() = test { client ->
    routing { put("/error-test") { call.receive<TestJson>() } }
    val invalidJson = "{ \"foo\": \"four\", \"bar\": \"twenty\" }"

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(error.errorReason, ErrorReason.InvalidParameter)
    assert(error.errorMessage!!.startsWith("Illegal input"))
  }

  @Test
  fun invalidJsonFieldTypeUuid() = test { client ->
    @Serializable data class TestJsonUuid(val foo: KUUID)
    routing { put("/error-test") { call.receive<TestJsonUuid>() } }
    val invalidJson = "{ \"foo\": \"goofd\", \"bar\": \"twenty\" }"

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(error.errorReason, ErrorReason.InvalidParameter)
  }

  @Test
  fun missingJsonField() = test { client ->
    routing { put("/error-test") { call.receive<TestJson>() } }
    val invalidJson = "{ \"foo\": \"John\" }"

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(error.errorReason, ErrorReason.InvalidParameter)
  }

  @Test
  fun invalidUuidInPath() = test { client ->
    routing { put("/error/{id}") { KUUID.fromString(call.parameters["id"]) } }
    val response = client.put("/error/foo") { contentType(ContentType.Application.Json) }
    val error = response.body<AppError>()
    assertEquals(400, response.status.value)
    assertEquals(error.errorReason, ErrorReason.InvalidParameter)
  }

  @Test
  fun notBlankValidationFailure() = test { client ->
    @Serializable
    data class ValidationTestJson(@NotBlank(message = "You done goof'd") val foo: String) :
      Validation

    routing {
      put("/error-test") {
        // This is done automatically by KTOR, however
        // the validation plugin is already installed in the global
        // app and this is the most painless way of simulating this
        // behaviour
        val data = call.receive<ValidationTestJson>()
        val results = data.validate()
        if (results is ValidationResult.Invalid) {
          throw RequestValidationException(call, reasons = results.reasons)
        }
      }
    }

    val invalidJson = "{ \"foo\": \"   \" }"

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<List<ValidationError>>()[0]

    assertEquals(422, response.status.value)
    assertEquals("notBlank", error.code)
    assertEquals("foo", error.fieldName)
    assertEquals("You done goof'd", error.message)
  }

  @Test
  fun rangeValidationFailure() = test { client ->
    @Serializable
    data class ValidationTestJson(@Range(min = 0.0, max = 1.0) val foo: Double) : Validation

    routing {
      put("/error-test") {
        val data = call.receive<ValidationTestJson>()
        val results = data.validate()
        if (results is ValidationResult.Invalid) {
          throw RequestValidationException(call, reasons = results.reasons)
        }
      }
    }

    val invalidJson = "{ \"foo\": 6.9 }"

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<List<ValidationError>>()[0]

    assertEquals(422, response.status.value)
    assertEquals("range", error.code)
    assertEquals("foo", error.fieldName)
    assertEquals("Value must be in range 0.0 - 1.0", error.message)
  }

  @Test
  fun rangeUpdateValidationFailure() = test { client ->
    @Serializable
    data class ValidationTestJson(@Range(min = 0.0, max = 1.0) val foo: PropertyUpdate<Double>) :
      Validation

    routing {
      put("/error-test") {
        val data = call.receive<ValidationTestJson>()
        val results = data.validate()
        if (results is ValidationResult.Invalid) {
          throw RequestValidationException(call, reasons = results.reasons)
        }
      }
    }

    val invalidJson = "{ \"foo\": 6.9 }"

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<List<ValidationError>>()[0]

    assertEquals(422, response.status.value)
    assertEquals("range", error.code)
    assertEquals("foo", error.fieldName)
    assertEquals("Value must be in range 0.0 - 1.0", error.message)
  }

  @Test
  fun happyPathUuid() = test { client ->
    routing {
      get("/happy/{path}") {
        call.pathUuid("path")
        call.respond(HttpStatusCode.OK)
      }
    }
    val id = KUUID.randomUUID()
    val response = client.get("/happy/$id") {}

    assertEquals(200, response.status.value)
  }

  @Test
  fun sadPathUuid() = test { client ->
    routing {
      get("/sad/{path}") {
        call.pathUuid("path")
        call.respond(HttpStatusCode.OK)
      }
    }

    val response = client.get("/sad/foo") {}

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(ErrorReason.InvalidParameter, error.errorReason)
    assert(error.errorMessage!!.contains("not a valid UUID"))
  }
}
