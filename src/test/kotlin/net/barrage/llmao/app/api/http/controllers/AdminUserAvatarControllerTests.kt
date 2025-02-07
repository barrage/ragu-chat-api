package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminUserAvatarControllerTests : IntegrationTest(useMinio = true) {
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
