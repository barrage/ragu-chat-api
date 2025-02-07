package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.User
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

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

    assertEquals(200, response.status.value)
    val body = response.body<User>()
    assertEquals(user.id, body.id)
    assertEquals("image/jpeg", body.avatar?.contentType?.toString())

    assertDoesNotThrow {
      minio.client.removeObject(
        RemoveObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.jpg").build()
      )
    }
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

    assertEquals(200, response.status.value)
    val body = response.body<User>()
    assertEquals(user.id, body.id)

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.png").build()
      )
    }

    assertDoesNotThrow {
      minio.client.removeObject(
        RemoveObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.png").build()
      )
    }
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

    assertEquals(200, responseUpload.status.value)
    val bodyUpload = responseUpload.body<User>()
    assertEquals(user.id, bodyUpload.id)

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.jpg").build()
      )
    }

    val responseDelete =
      client.delete("/users/avatars") { header("Cookie", sessionCookie(userSession.sessionId)) }

    assertEquals(204, responseDelete.status.value)

    assertThrows<ErrorResponseException> {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.jpg").build()
      )
    }
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
    val responseUpload1 =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/jpeg")
        setBody("test".toByteArray())
      }

    assertEquals(200, responseUpload1.status.value)
    val bodyUpload1 = responseUpload1.body<User>()
    assertEquals(user.id, bodyUpload1.id)
    assertEquals("image/jpeg", bodyUpload1.avatar?.contentType?.toString())

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.jpg").build()
      )
    }

    val responseUpload2 =
      client.post("/users/avatars") {
        header("Cookie", sessionCookie(userSession.sessionId))
        header("Content-Type", "image/png")
        setBody("test".toByteArray())
      }

    assertEquals(200, responseUpload2.status.value)
    val bodyUpload2 = responseUpload2.body<User>()
    assertEquals(user.id, bodyUpload2.id)
    assertEquals("image/png", bodyUpload2.avatar?.contentType?.toString())

    assertDoesNotThrow {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.png").build()
      )
    }

    assertThrows<ErrorResponseException> {
      minio.client.statObject(
        StatObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.jpg").build()
      )
    }
  }

  @Test
  fun getUserWithAvatar() = test {
    minio.client.putObject(
      PutObjectArgs.builder()
        .bucket("test")
        .`object`("avatars/${user.id}.jpg")
        .stream("test".byteInputStream(), 4, -1)
        .build()
    )
    val client = createClient { install(ContentNegotiation) { json() } }
    val responseWithoutAvatar =
      client.get("/users/current") { header("Cookie", sessionCookie(userSession.sessionId)) }
    assertEquals(200, responseWithoutAvatar.status.value)
    val bodyWithoutAvatar = responseWithoutAvatar.body<User>()
    assertNull(bodyWithoutAvatar.avatar)

    val responseWithAvatar =
      client.get("/users/current") {
        header("Cookie", sessionCookie(userSession.sessionId))
        parameter("withAvatar", "true")
      }
    assertEquals(200, responseWithAvatar.status.value)
    val bodyWithAvatar = responseWithAvatar.body<User>()
    assertNotNull(bodyWithAvatar.avatar)

    minio.client.removeObject(
      RemoveObjectArgs.builder().bucket("test").`object`("avatars/${user.id}.jpg").build()
    )
  }
}
