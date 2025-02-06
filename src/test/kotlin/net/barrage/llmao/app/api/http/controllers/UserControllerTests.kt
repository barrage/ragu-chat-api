package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.User
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class UserControllerTests : IntegrationTest() {
  private lateinit var user: User
  private lateinit var userSession: Session

  @BeforeAll
  fun setup() {
    runBlocking {
      user = postgres.testUser(admin = false)
      userSession = postgres.testSession(user.id)
    }
  }

  @Test
  fun shouldRetrieveCurrentUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/users/current") { header("Cookie", sessionCookie(userSession.sessionId)) }

    assertEquals(200, response.status.value)
    val body = response.body<User>()
    assertEquals(user.id, body.id)
  }

  @Test
  fun shouldFailGetCurrentUserWithoutSession() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.get("/users/current")

    assertEquals(401, response.status.value)
    val body = response.body<String>()
    assertEquals("Unauthorized access", body)
  }

  @Test
  fun shouldUpdateUser() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val updateUser = UpdateUser(fullName = "First Last", firstName = "First", lastName = "Last")
    val response =
      client.put("/users") {
        header("Cookie", sessionCookie(userSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(updateUser)
      }

    assertEquals(200, response.status.value)
    val body = response.body<User>()
    assertEquals(updateUser.fullName, body.fullName)
    assertEquals(updateUser.firstName, body.firstName)
    assertEquals(updateUser.lastName, body.lastName)
  }

  @Test
  fun shouldUploadAvatarJpeg() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)

    val responseCheck =
      client.get("/avatars/${user.id}.jpeg") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/jpeg", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @Test
  fun shouldUploadAvatarPng() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/png")
        setBody("test".toByteArray())
      }

    assertEquals(201, response.status.value)

    val responseCheck =
      client.get("/avatars/${user.id}.png") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)
    assertEquals("image/png", responseCheck.headers["Content-Type"])
    assertEquals("test", responseCheck.bodyAsText())
  }

  @Test
  fun shouldDeleteAvatar() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val responseUpload =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, responseUpload.status.value)

    val responseCheck =
      client.get("/avatars/${user.id}.jpeg") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, responseCheck.status.value)

    val responseDelete =
      client.delete("/users/avatars") { header("Cookie", sessionCookie(userSession.sessionId)) }

    assertEquals(204, responseDelete.status.value)

    val responseCheckAvatar =
      client.get("/avatars/${user.id}.jpeg") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(404, responseCheckAvatar.status.value)
  }

  @Test
  fun shouldFailUploadAvatarWrongContentType() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
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
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test123".repeat(1000000).toByteArray())
      }

    assertEquals(413, response.status.value)
  }

  @Test
  fun shouldReplaceAvatar() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val responseUploadOriginal =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(201, responseUploadOriginal.status.value)

    val responseCheckOriginal =
      client.get("/avatars/${user.id}.jpeg") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, responseCheckOriginal.status.value)
    assertEquals("image/jpeg", responseCheckOriginal.headers["Content-Type"])
    assertEquals("test", responseCheckOriginal.bodyAsText())

    val responseUploadOverwrite =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/png")
        setBody("overwrite".toByteArray())
      }

    assertEquals(201, responseUploadOverwrite.status.value)

    val responseCheckOverwrite =
      client.get("/avatars/${user.id}.png") {
        header("Cookie", sessionCookie(userSession.sessionId))
      }

    assertEquals(200, responseCheckOverwrite.status.value)
    assertEquals("image/png", responseCheckOverwrite.headers["Content-Type"])
    assertEquals("overwrite", responseCheckOverwrite.bodyAsText())
  }
}
