package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import net.barrage.llmao.TestClass
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.User

class UserControllerTests : TestClass() {
  private val user: User = postgres!!.testUser(admin = false)
  private val userSession: Session = postgres!!.testSession(user.id)

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
}