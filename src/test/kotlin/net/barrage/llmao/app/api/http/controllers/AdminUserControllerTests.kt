package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.barrage.llmao.TestClass
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.utils.ValidationError
import org.junit.Test

class AdminUserControllerTests : TestClass() {
  private val adminUser = postgres!!.testUser("foo@bar.com", admin = true)
  private val peasantUser = postgres!!.testUser("bar@foo.com", admin = false)
  private val adminSession = postgres!!.testSession(adminUser.id)

  @Test
  fun listAllUsersDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("admin/users") { header("Cookie", sessionCookie(adminSession.sessionId)) }

    assertEquals(200, response.status.value)
    val body: CountedList<User> = response.body()!!
    assertEquals(2, body.total)
    assertTrue { body.items.any { it.id == adminUser.id } }
    assertTrue { body.items.any { it.id == peasantUser.id } }
  }

  @Test
  fun createUserSuccess() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("admin/users") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          mapOf(
            "email" to "create@user.ban",
            "fullName" to "Create User",
            "firstName" to "Create",
            "lastName" to "User",
            "role" to "user",
          )
        )
      }

    assertEquals(201, response.status.value)
    val body: User = response.body()!!
    assertEquals("create@user.ban", body.email)
    assertEquals("Create User", body.fullName)
    assertEquals("Create", body.firstName)
    assertEquals("User", body.lastName)
    assertEquals("USER", body.role)
    postgres!!.deleteTestUser(body.id)
  }

  @Test
  fun createUserValidationFail() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("admin/users") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          """{
            "email": "invalid-email",
            "fullName": "",
            "firstName": "",
            "lastName": "",
            "role": "user"
          }"""
        )
      }

    assertEquals(422, response.status.value)
    val body: List<ValidationError> = response.body()!!
    assertEquals(4, body.size)
    body.forEach {
      when (it.fieldName) {
        "email" -> {
          assertEquals("email", it.code)
          assertEquals("email", it.fieldName)
          assertEquals("Value is not valid email", it.message)
        }

        else -> {
          assertEquals("notBlank", it.code)
          assertEquals("Value cannot be blank", it.message)
        }
      }
    }
  }

  @Test
  fun createUserFailsExistingEmail() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("admin/users") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          mapOf(
            "email" to adminUser.email,
            "fullName" to "Create User",
            "firstName" to "Create",
            "lastName" to "User",
            "role" to "user",
          )
        )
      }

    assertEquals(409, response.status.value)
    val body: AppError = response.body()!!
    assertEquals(ErrorReason.EntityAlreadyExists, body.reason)
  }

  @Test
  fun getSingleUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("admin/users/${adminUser.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val result: User = response.body()!!
    assertEquals(adminUser.id, result.id)
    assertEquals(adminUser.email, result.email)
    assertEquals(adminUser.fullName, result.fullName)
    assertEquals(adminUser.firstName, result.firstName)
    assertEquals(adminUser.lastName, result.lastName)
    assertEquals(adminUser.role, result.role)
  }

  @Test
  fun getSingleUserFailsUserNotFound() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("admin/users/${UUID.randomUUID()}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(404, response.status.value)
    val body: AppError = response.body()!!
    assertEquals(ErrorReason.EntityDoesNotExist, body.reason)
  }

  @Test
  fun updateUserSuccess() = test {
    val testUser = postgres!!.testUser("update@user.net", admin = true, active = false)
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("admin/users/${peasantUser.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          """{
            "fullName": "Updated User",
            "firstName": "Updated",
            "lastName": "User",
            "email": "updated@user.com",
            "role": "user",
            "active": true
          }"""
        )
      }

    assertEquals(200, response.status.value)
    val result: User = response.body()!!
    assertEquals(peasantUser.id, result.id)
    assertEquals("updated@user.com", result.email)
    assertEquals("Updated User", result.fullName)
    assertEquals("Updated", result.firstName)
    assertEquals("User", result.lastName)
    assertEquals("USER", result.role)
    assertTrue(result.active)

    postgres.deleteTestUser(testUser.id)
  }

  @Test
  fun updateUserFailsUserNotFound() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("admin/users/${UUID.randomUUID()}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          """{
            "fullName": "Updated User",
            "firstName": "Updated",
            "lastName": "User",
            "email": "updated@user.com",
            "role": "user",
            "active": true
          }"""
        )
      }

    assertEquals(404, response.status.value)
    val body: AppError = response.body()!!
    assertEquals(ErrorReason.EntityDoesNotExist, body.reason)
  }

  @Test
  fun updateUserFailsValidation() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val reponse =
      client.put("admin/users/${UUID.randomUUID()}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          """{
            "fullName": "",
            "firstName": "",
            "lastName": "",
            "email": "",
            "role": "user",
            "active": true
          }"""
        )
      }

    assertEquals(422, reponse.status.value)
    val body: List<ValidationError> = reponse.body()!!
    assertEquals(4, body.size)
    body.forEach {
      when (it.fieldName) {
        "email" -> {
          assertEquals("email", it.code)
          assertEquals("email", it.fieldName)
          assertEquals("Value is not valid email", it.message)
        }

        else -> {
          assertEquals("notBlank", it.code)
          assertEquals("Value cannot be blank", it.message)
        }
      }
    }
  }

  @Test
  fun deleteUserSuccess() = test {
    val testUser = postgres!!.testUser(admin = false)
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("admin/users/${testUser.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, response.status.value)
  }

  @Test
  fun deleteUserFailsUserNotFound() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("admin/users/${UUID.randomUUID()}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(404, response.status.value)
    val body: AppError = response.body()!!
    assertEquals(ErrorReason.EntityDoesNotExist, body.reason)
  }
}