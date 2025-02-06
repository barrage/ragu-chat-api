package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.OffsetDateTime
import java.util.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.CsvImportErrorType
import net.barrage.llmao.core.models.CsvImportUsersResult
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import net.barrage.llmao.tables.references.USERS
import net.barrage.llmao.utils.ValidationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminUserControllerTests : IntegrationTest() {
  private lateinit var adminUser: User
  private lateinit var peasantUser: User
  private lateinit var inactivePeasantUser: User
  private lateinit var deletedUser: User
  private lateinit var adminSession: Session

  @BeforeAll
  fun setup() {
    runBlocking {
      adminUser =
        postgres.testUser(
          "foo@bar.com",
          admin = true,
          fullName = "adminko test",
          firstName = "adminko",
          lastName = "test",
        )
      peasantUser = postgres.testUser("bar@foo.com", admin = false)
      inactivePeasantUser = postgres.testUser("inactive@foo.com", admin = false, active = false)
      deletedUser = // added for testing list users
        postgres.testUser("deleted@user.me", admin = false, deletedAt = OffsetDateTime.now())
      adminSession = postgres.testSession(adminUser.id)
    }
  }

  @Test
  fun listAllUsersDefaultPagination() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("admin/users") { header("Cookie", sessionCookie(adminSession.sessionId)) }

    assertEquals(200, response.status.value)
    val body: CountedList<User> = response.body()!!
    assertEquals(4, body.total) // default Admin user
    assertTrue { body.items.any { it.id == adminUser.id } }
    assertTrue { body.items.any { it.id == peasantUser.id } }
  }

  @Test
  fun listAllUsersFilterByName() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("admin/users") {
        parameter("name", "adminko")
        header("Cookie", sessionCookie(adminSession.sessionId))
      }
    println(response.bodyAsText())
    assertEquals(200, response.status.value)
    val body: CountedList<User> = response.body()!!
    assertEquals(1, body.total)
    assertTrue { body.items.any { it.id == adminUser.id } }
  }

  @Test
  fun listAllUsersFilterByRole() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("admin/users") {
        parameter("role", "user")
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body: CountedList<User> = response.body()!!
    assertEquals(2, body.total)
    assertTrue { body.items.any { it.id == peasantUser.id } }
    assertTrue { body.items.any { it.id == inactivePeasantUser.id } }
  }

  @Test
  fun listAllUsersFilterByActive() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("admin/users") {
        parameter("active", "false")
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
    val body: CountedList<User> = response.body()!!
    assertEquals(1, body.total)
    assertTrue { body.items.any { it.id == inactivePeasantUser.id } }
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
    assertEquals(Role.USER, body.role)
    postgres.deleteTestUser(body.id)
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
    assertEquals(ErrorReason.EntityAlreadyExists, body.errorReason)
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
    assertEquals(ErrorReason.EntityDoesNotExist, body.errorReason)
  }

  @Test
  fun updateUserSuccess() = test {
    val testUser = postgres.testUser("update@user.net", admin = true, active = false)
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("admin/users/${testUser.id}") {
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
    assertEquals(testUser.id, result.id)
    assertEquals("updated@user.com", result.email)
    assertEquals("Updated User", result.fullName)
    assertEquals("Updated", result.firstName)
    assertEquals("User", result.lastName)
    assertEquals(Role.USER, result.role)
    assertTrue(result.active)

    postgres.deleteTestUser(testUser.id)
  }

  @Test
  fun updateUserFailsUpdateRoleOnSelf() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("admin/users/${adminSession.userId}") {
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

    assertEquals(409, response.status.value)
    val body: AppError = response.body()!!
    assertEquals(ErrorReason.CannotUpdateSelf, body.errorReason)
    assertEquals("Cannot update Role on self", body.errorDescription)
  }

  @Test
  fun updateUserFailsUpdateActiveOnSelf() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.put("admin/users/${adminSession.userId}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "application/json")
        setBody(
          """{
            "fullName": "Updated User",
            "firstName": "Updated",
            "lastName": "User",
            "email": "updated@user.com",
            "role": "admin",
            "active": false
          }"""
        )
      }

    assertEquals(409, response.status.value)
    val body: AppError = response.body()!!
    assertEquals(ErrorReason.CannotUpdateSelf, body.errorReason)
    assertEquals("Cannot update Active on self", body.errorDescription)
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
    assertEquals(ErrorReason.EntityDoesNotExist, body.errorReason)
  }

  @Test
  fun updateUserFailsValidation() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
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
  fun deleteUserSuccess() = test {
    val testUser = postgres.testUser(admin = false)
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("admin/users/${testUser.id}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, response.status.value)

    val deletedUser =
      postgres.dslContext.selectFrom(USERS).where(USERS.ID.eq(testUser.id)).awaitSingle().toUser()

    assertEquals(testUser.id, deletedUser.id)
    assertTrue(deletedUser.deletedAt != null)
    assertEquals("${testUser.id}@deleted.net", deletedUser.email)
    assertEquals("deleted", deletedUser.fullName)
    assertEquals("deleted", deletedUser.firstName)
    assertEquals("deleted", deletedUser.lastName)
  }

  @Test
  fun deleteUserFailsDeleteSelf() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.delete("admin/users/${adminSession.userId}") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(409, response.status.value)
    val body: AppError = response.body()!!
    assertEquals(ErrorReason.CannotDeleteSelf, body.errorReason)
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
    assertEquals(ErrorReason.EntityDoesNotExist, body.errorReason)
  }

  @Test
  fun importUsersCsvSuccess() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/import-csv") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", ContentType.Text.CSV)
        setBody(
          """
          FullName,FirstName,LastName,Email,Role
          Test User,Test,User,test@user.net,user
        """
            .trimIndent()
        )
      }

    assertEquals(200, response.status.value)
    val body = response.body<CsvImportUsersResult>()
    assertEquals(1, body.successful.size)
    assertEquals(0, body.failed.size)

    assertEquals(Role.USER, body.successful[0].role)
    assertEquals("test@user.net", body.successful[0].email)

    val user =
      postgres.dslContext
        .selectFrom(USERS)
        .where(USERS.EMAIL.eq("test@user.net"))
        .awaitSingle()
        .toUser()

    assertEquals("Test User", user.fullName)
    assertEquals("Test", user.firstName)
    assertEquals("User", user.lastName)
    assertEquals(Role.USER, user.role)

    postgres.deleteTestUser(user.id)
  }

  @Test
  fun importUsersCsvWrongContentType() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/import-csv") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", ContentType.Text.Plain)
        setBody(
          """
          FullName,FirstName,LastName,Email,Role
          Test User,Test,User,test@user.net,user
        """
            .trimIndent()
        )
      }

    assertEquals(400, response.status.value)
  }

  @Test
  fun importUsersCsvUserAlreadyExists() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/import-csv") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", ContentType.Text.CSV)
        setBody(
          """
          FullName,FirstName,LastName,Email,Role
          Test User,Test,User,test@user.net,user
          Test User,Test,User,foo@bar.com,user
        """
            .trimIndent()
        )
      }

    assertEquals(200, response.status.value)
    val body = response.body<CsvImportUsersResult>()
    assertEquals(2, body.successful.size)
    assertEquals(0, body.failed.size)

    assertEquals(Role.USER, body.successful[0].role)
    assertEquals("test@user.net", body.successful[0].email)
    assertEquals(Role.USER, body.successful[1].role)
    assertEquals("foo@bar.com", body.successful[1].email)

    val user =
      postgres.dslContext
        .selectFrom(USERS)
        .where(USERS.EMAIL.eq("test@user.net"))
        .awaitSingle()
        .toUser()

    assertEquals("Test User", user.fullName)
    assertEquals("Test", user.firstName)
    assertEquals("User", user.lastName)
    assertEquals(Role.USER, user.role)

    postgres.deleteTestUser(user.id)
  }

  @Test
  fun importUsersCsvInvalidLines() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/import-csv") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", ContentType.Text.CSV)
        setBody(
          """
          FullName,FirstName,LastName,Email,Role
          Test User,Test,User,test@user.net
          Test User,Test,User,test@user.net,used
          ,,,,user
        """
            .trimIndent()
        )
      }

    assertEquals(200, response.status.value)
    val body = response.body<CsvImportUsersResult>()

    assertEquals(0, body.successful.size)
    assertEquals(3, body.failed.size)
    assertEquals(CsvImportErrorType.MISSING_FIELDS, body.failed[0].type)
    assertEquals(CsvImportErrorType.VALIDATION, body.failed[1].type)
    assertEquals(CsvImportErrorType.VALIDATION, body.failed[2].type)
    assertEquals(CsvImportErrorType.VALIDATION, body.failed[2].type)
  }

  @Test
  fun importUsersCsvTooLarge() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/import-csv") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", ContentType.Text.CSV)
        setBody("FullName,FirstName,LastName,Email,Role\n".repeat(1000000))
      }

    assertEquals(413, response.status.value)
  }

  @Test
  fun importUsersCsvEmpty() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/import-csv") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", ContentType.Text.CSV)
        setBody("")
      }

    assertEquals(400, response.status.value)
  }

  @Test
  fun importUsersCsvInvalidHeader() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/import-csv") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", ContentType.Text.CSV)
        setBody("FullName,FirstName,LastName,Email,Roled")
      }
    assertEquals(400, response.status.value)
    assertEquals(ErrorReason.InvalidParameter, response.body<AppError>().errorReason)
    assertEquals("Invalid CSV header", response.body<AppError>().errorDescription)
  }

  @Test
  fun shouldUploadAvatarJpeg() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)

    val responseCheck =
      client.get("/avatars/${peasantUser.id}.jpeg") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/jpeg", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @Test
  fun shouldUploadAvatarPng() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/png")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)

    val responseCheck =
      client.get("/avatars/${peasantUser.id}.png") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/png", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @Test
  fun shouldDeleteAvatar() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val responseUpload =
      client.post("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, responseUpload.status.value)

    val responseDelete =
      client.delete("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }

    assertEquals(204, responseDelete.status.value)

    val responseCheck =
      client.get("/admin/users/${peasantUser.id}") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertNull(responseCheck.body<User>().avatar)

    val responseCheckAvatar =
      client.get("/avatars/${peasantUser.id}.jpeg") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(404, responseCheckAvatar.status.value)
  }

  @Test
  fun shouldFailUploadAvatarWrongContentType() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "text/plain")
        setBody("test".toByteArray())
      }

    assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    assertEquals("API", body.errorType)
    assertEquals(ErrorReason.InvalidContentType, body.errorReason)
  }

  @Test
  fun shouldFailUploadAvatarTooLarge() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test123".repeat(1000000).toByteArray())
      }

    assertEquals(413, response.status.value)
  }

  @Test
  fun shouldReplaceAvatar() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val responseUploadOriginal =
      client.post("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("foo".toByteArray())
      }

    assertEquals(201, responseUploadOriginal.status.value)

    val originalPath = responseUploadOriginal.bodyAsText()
    assertEquals("${peasantUser.id}.jpeg", originalPath)

    val responseCheckOriginal =
      client.get("/avatars/${peasantUser.id}.jpeg") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }
    assertEquals(200, responseCheckOriginal.status.value)
    assertEquals("image/jpeg", responseCheckOriginal.headers["Content-Type"])
    assertEquals("foo", responseCheckOriginal.bodyAsText())

    val responseUploadOverwrite =
      client.post("/admin/users/${peasantUser.id}/avatars") {
        header("Cookie", sessionCookie(adminSession.sessionId))
        header("Content-Type", "image/png")
        setBody("bar".toByteArray())
      }

    assertEquals(201, responseUploadOverwrite.status.value)

    val responseCheckOverwrite =
      client.get("/avatars/${peasantUser.id}.png") {
        header("Cookie", sessionCookie(adminSession.sessionId))
      }
    assertEquals(200, responseCheckOverwrite.status.value)
    assertEquals("image/png", responseCheckOverwrite.headers["Content-Type"])
    assertEquals("bar", responseCheckOverwrite.bodyAsText())
  }
}
