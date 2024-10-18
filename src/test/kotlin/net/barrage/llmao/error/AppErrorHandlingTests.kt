package net.barrage.llmao.error

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.plugins.pathUuid
import net.barrage.llmao.utils.NotBlank
import net.barrage.llmao.utils.Range
import net.barrage.llmao.utils.Validation
import net.barrage.llmao.utils.ValidationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppErrorHandlingTests : IntegrationTest(false, false) {

  @Serializable data class TestJson(val foo: String, val bar: Int)

  @Test
  fun invalidHttpMethod() = test {
    routing { put("/error-test") { call.receive<TestJson>() } }
    val response = client.post("/error-test")
    assertEquals(405, response.status.value)
  }

  @Test
  fun invalidJsonFieldType() = test {
    routing { put("/error-test") { call.receive<TestJson>() } }
    val invalidJson = "{ \"foo\": \"four\", \"bar\": \"twenty\" }"

    val client = createClient { install(ContentNegotiation) { json() } }

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(error.reason, ErrorReason.InvalidParameter)
    assert(error.description!!.startsWith("Illegal input"))
  }

  @Test
  fun invalidJsonFieldTypeUuid() = test {
    @Serializable data class TestJsonUuid(val foo: KUUID)
    routing { put("/error-test") { call.receive<TestJsonUuid>() } }
    val invalidJson = "{ \"foo\": \"goofd\", \"bar\": \"twenty\" }"

    val client = createClient { install(ContentNegotiation) { json() } }

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(error.reason, ErrorReason.InvalidParameter)
  }

  @Test
  fun missingJsonField() = test {
    routing { put("/error-test") { call.receive<TestJson>() } }
    val invalidJson = "{ \"foo\": \"John\" }"

    val client = createClient { install(ContentNegotiation) { json() } }

    val response =
      client.put("/error-test") {
        contentType(ContentType.Application.Json)
        setBody(invalidJson)
      }

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(error.reason, ErrorReason.InvalidParameter)
  }

  @Test
  fun invalidUuidInPath() = test {
    routing { put("/error/{id}") { KUUID.fromString(call.parameters["id"]) } }
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.put("/error/foo") { contentType(ContentType.Application.Json) }
    val error = response.body<AppError>()
    assertEquals(400, response.status.value)
    assertEquals(error.reason, ErrorReason.InvalidParameter)
  }

  @Test
  fun notBlankValidationFailure() = test {
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

    val client = createClient { install(ContentNegotiation) { json() } }

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
  fun rangeValidationFailure() = test {
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

    val client = createClient { install(ContentNegotiation) { json() } }

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
  fun happyPathUuid() = test {
    routing {
      get("/happy/{path}") {
        call.pathUuid("path")
        call.respond(HttpStatusCode.OK)
      }
    }

    val client = createClient { install(ContentNegotiation) { json() } }
    val id = KUUID.randomUUID()
    val response = client.get("/happy/$id") {}

    assertEquals(200, response.status.value)
  }

  @Test
  fun sadPathUuid() = test {
    routing {
      get("/sad/{path}") {
        call.pathUuid("path")
        call.respond(HttpStatusCode.OK)
      }
    }

    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.get("/sad/foo") {}

    val error = response.body<AppError>()

    assertEquals(400, response.status.value)
    assertEquals(ErrorReason.InvalidParameter, error.reason)
    assert(error.description!!.contains("not a valid UUID"))
  }
}
