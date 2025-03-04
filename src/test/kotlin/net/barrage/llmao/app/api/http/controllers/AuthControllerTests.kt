package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.OffsetDateTime
import java.util.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.toSessionData
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import net.barrage.llmao.tables.references.SESSIONS
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AuthControllerTests : IntegrationTest(useWiremock = true) {
  lateinit var user: User
  lateinit var admin: User

  @BeforeAll
  fun setup() {
    runBlocking {
      user =
        postgres.testUser(
          email = "test@user.me",
          admin = false,
        ) // must match wiremock response email
      admin = postgres.testUser(email = "admin@user.me", admin = true)
      postgres.testUser(email = "deleted@user.me", admin = false, active = false)
    }
  }

  // Apple login tests
  @Test
  fun shouldLoginUserApple() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "success")
              append("redirect_uri", "redirect_uri")
              append("provider", "apple")
              append("source", "web")
              append("code_verifier", "success")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(204, response.status.value)
    Assertions.assertTrue(response.headers.contains("Set-Cookie"))
    val cookies = response.setCookie()
    Assertions.assertEquals(1, cookies.size)
    val kappi = cookies.firstOrNull { it.name == "kappi" }
    Assertions.assertNotNull(kappi)
    val cookieVal = kappi!!.value
    Assertions.assertTrue(cookieVal.startsWith("id=%23s"))
    val sessionId = cookieVal.substringAfter("id=%23s")
    postgres.dslContext
      .deleteFrom(SESSIONS)
      .where(SESSIONS.ID.eq(UUID.fromString(sessionId)))
      .awaitSingle()
  }

  @Test
  fun shouldThrowErrorOnLoginUserAppleTokenError() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "error")
              append("redirect_uri", "redirect_uri")
              append("provider", "apple")
              append("source", "web")
              append("code_verifier", "error")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
  }

  @Test
  fun shouldThrowErrorOnLoginUserAppleWrongKid() = test {
    wiremock!!.resetScenarios()
    wiremock!!.setScenarioState("AppleAuthKeys", "WrongKID")
    val client = createClient {
      install(ContentNegotiation) { json() }
      defaultRequest { header("X-Test-Scenario", "WrongKID") }
    }

    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "wrong_kid")
              append("redirect_uri", "redirect_uri")
              append("provider", "apple")
              append("source", "web")
              append("code_verifier", "wrong_kid")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
    Assertions.assertEquals(
      "Unable to validate token signature; Unable to match public key!",
      body.errorDescription,
    )

    wiremock!!.resetScenarios()
  }

  @Test
  fun shouldThrowErrorOnLoginUserAppleMissingEmail() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "missing_email")
              append("redirect_uri", "redirect_uri")
              append("provider", "apple")
              append("source", "web")
              append("code_verifier", "missing_email")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
    Assertions.assertEquals("Email not found", body.errorDescription)
  }

  // Google login tests

  @Test
  fun shouldLoginUserGoogle() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "success")
              append("redirect_uri", "redirect_uri")
              append("provider", "google")
              append("source", "web")
              append("code_verifier", "success")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(204, response.status.value)
    Assertions.assertTrue(response.headers.contains("Set-Cookie"))
    val cookies = response.setCookie()
    Assertions.assertEquals(2, cookies.size)
    val profileCookie = cookies.firstOrNull { it.name == "user_picture" }!!
    Assertions.assertEquals("https://not.set.com", profileCookie.value)
    val sessionCookie = cookies.firstOrNull { it.name == "kappi" }!!
    Assertions.assertTrue(sessionCookie.name == "kappi")
    val cookieVal = sessionCookie.value
    Assertions.assertTrue(cookieVal.startsWith("id=%23s"))
    val sessionId = cookieVal.substringAfter("id=%23s")
    postgres.dslContext
      .deleteFrom(SESSIONS)
      .where(SESSIONS.ID.eq(UUID.fromString(sessionId)))
      .awaitSingle()
  }

  @Test
  fun shouldThrowErrorOnLoginUserGoogleTokenError() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "error")
              append("redirect_uri", "redirect_uri")
              append("provider", "google")
              append("source", "web")
              append("code_verifier", "error")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
  }

  @Test
  fun shouldThrowErrorOnLoginUserGoogleWrongKid() = test {
    wiremock!!.resetScenarios()
    wiremock!!.setScenarioState("GoogleAuthKeys", "WrongKID")
    val client = createClient {
      install(ContentNegotiation) { json() }
      defaultRequest { header("X-Test-Scenario", "WrongKID") }
    }

    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "wrong_kid")
              append("redirect_uri", "redirect_uri")
              append("provider", "google")
              append("source", "web")
              append("code_verifier", "wrong_kid")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
    Assertions.assertEquals(
      "Unable to validate token signature; Unable to match public key!",
      body.errorDescription,
    )

    wiremock!!.resetScenarios()
  }

  @Test
  fun shouldThrowErrorOnLoginUserGoogleMissingEmail() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "missing_email")
              append("redirect_uri", "redirect_uri")
              append("provider", "google")
              append("source", "web")
              append("code_verifier", "missing_email")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
    Assertions.assertEquals("Email not found", body.errorDescription)
  }

  @Test
  fun shouldThrowErrorOnLoginUserGoogleUnverifiedEmail() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "missing_email")
              append("redirect_uri", "redirect_uri")
              append("provider", "google")
              append("source", "web")
              append("code_verifier", "missing_email")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
    Assertions.assertEquals("Email not found", body.errorDescription)
  }

  // Carnet login tests

  @Test
  fun shouldLoginUserCarnet() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "success")
              append("redirect_uri", "redirect_uri")
              append("provider", "carnet")
              append("source", "web")
              append("code_verifier", "success")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(204, response.status.value)

    Assertions.assertTrue(response.headers.contains("Set-Cookie"))
    val cookies = response.setCookie()
    Assertions.assertEquals(1, cookies.size)
    Assertions.assertNotNull(cookies.firstOrNull { it.name == "kappi" })
    val cookieVal = cookies.first { it.name == "kappi" }.value
    Assertions.assertTrue(cookieVal.startsWith("id=%23s"))
    val sessionId = cookieVal.substringAfter("id=%23s")
    postgres.dslContext
      .deleteFrom(SESSIONS)
      .where(SESSIONS.ID.eq(UUID.fromString(sessionId)))
      .awaitSingle()
  }

  @Test
  fun shouldThrowErrorOnLoginAdminCarnetTokenError() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "error")
              append("redirect_uri", "redirect_uri")
              append("provider", "carnet")
              append("source", "web")
              append("code_verifier", "error")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
  }

  @Test
  fun shouldThrowErrorOnLoginAdminCarnetWrongKid() = test {
    wiremock!!.resetScenarios()
    wiremock!!.setScenarioState("CarnetAuthKeys", "WrongKID")
    val client = createClient {
      install(ContentNegotiation) { json() }
      defaultRequest { header("X-Test-Scenario", "WrongKID") }
    }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "wrong_kid")
              append("redirect_uri", "redirect_uri")
              append("provider", "carnet")
              append("source", "web")
              append("code_verifier", "wrong_kid")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(401, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.Authentication, body.errorReason)
    Assertions.assertEquals(
      "Unable to validate token signature; Unable to match public key!",
      body.errorDescription,
    )

    wiremock!!.resetScenarios()
  }

  @Test
  fun shouldThrowErrorOnLoginAdminCarnetUnknownUser() = test {
    wiremock!!.resetScenarios()
    wiremock!!.setScenarioState("CarnetUserInfo", "UnknownUser")
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/login") {
        header("Content-Type", "application/x-www-form-urlencoded")
        setBody(
          FormDataContent(
            Parameters.build {
              append("code", "unknown_user")
              append("redirect_uri", "redirect_uri")
              append("provider", "carnet")
              append("source", "web")
              append("code_verifier", "unknown_user")
              append("grant_type", "authorization_code")
            }
          )
        )
      }

    Assertions.assertEquals(404, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.EntityDoesNotExist, body.errorReason)
    Assertions.assertEquals("User 'unknown@user.me' does not exist", body.errorDescription)
  }

  // Logout tests

  @Test
  fun shouldLogoutUser() = test {
    val userSession = postgres.testSession(user.id)
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/logout") { header("Cookie", sessionCookie(userSession.sessionId)) }
    Assertions.assertEquals(204, response.status.value)
    val session =
      postgres.dslContext
        .selectFrom(SESSIONS)
        .where(SESSIONS.ID.eq(userSession.sessionId))
        .awaitSingle()
        .toSessionData()

    Assertions.assertTrue(session.expiresAt.isBefore(OffsetDateTime.now()))
  }

  @Test
  fun shouldLogoutUserWithoutSession() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response = client.post("/auth/logout")
    Assertions.assertEquals(204, response.status.value)
  }
}
