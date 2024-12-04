package net.barrage.llmao.app.adapters

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.util.*
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAuthentication
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAuthenticationRequest
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChonkitAuthenticationAdapterTests : IntegrationTest(enableChonkitAuth = true) {
  private lateinit var chadUser: User
  private lateinit var peasantUser: User
  private lateinit var chadSession: Session
  private lateinit var peasantSession: Session

  @BeforeAll
  fun setup() {
    chadUser = postgres.testUser(email = "chad@neovim.bro", admin = true)
    peasantUser = postgres.testUser(email = "peasant@vscope.soy", admin = false)
    chadSession = postgres.testSession(chadUser.id)
    peasantSession = postgres.testSession(peasantUser.id)
  }

  @Test
  fun adminUserSuccessfullyGetsTokenPair() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/chonkit/token") { header("Cookie", sessionCookie(chadSession.sessionId)) }

    assertEquals(200, response.status.value)

    val cookies = response.setCookie()
    assertNotNull(cookies.first { it.name == "chonkit_access_token" })
    assertNotNull(cookies.first { it.name == "chonkit_refresh_token" })

    val body = response.body<ChonkitAuthentication>()

    assertNotNull(body.accessToken)
    assertNotNull(body.refreshToken)
  }

  @Test
  fun adminUserSuccessfullyRefreshesTokenPairViaCookie() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val authResponse =
      client.post("/auth/chonkit/token") { header("Cookie", sessionCookie(chadSession.sessionId)) }
    val response =
      client.post("/auth/chonkit/refresh") {
        for (cookie in authResponse.setCookie()) {
          cookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            maxAge = cookie.maxAge,
            expires = cookie.expires,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            extensions = cookie.extensions,
          )
        }
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(200, response.status.value)

    val cookies = response.setCookie()
    assertNotNull(cookies.first { it.name == "chonkit_access_token" })
    assertNotNull(cookies.first { it.name == "chonkit_refresh_token" })

    val body = response.body<ChonkitAuthentication>()

    assertNotNull(body.accessToken)
    assertNotNull(body.refreshToken)
  }

  @Test
  fun adminUserSuccessfullyRefreshesTokenPairViaBody() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val authResponse =
      client.post("/auth/chonkit/token") { header("Cookie", sessionCookie(chadSession.sessionId)) }
    val response =
      client.post("/auth/chonkit/refresh") {
        contentType(ContentType.Application.Json)
        setBody(
          ChonkitAuthenticationRequest(
            refreshToken = authResponse.body<ChonkitAuthentication>().refreshToken
          )
        )
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(200, response.status.value)

    val cookies = response.setCookie()
    assertNotNull(cookies.first { it.name == "chonkit_access_token" })
    assertNotNull(cookies.first { it.name == "chonkit_refresh_token" })

    val body = response.body<ChonkitAuthentication>()

    assertNotNull(body.accessToken)
    assertNotNull(body.refreshToken)
  }

  @Test
  fun refreshFailsIfBodyTokenNotValid() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/chonkit/refresh") {
        contentType(ContentType.Application.Json)
        setBody(ChonkitAuthenticationRequest(refreshToken = "invalid"))
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(401, response.status.value)

    val error = response.body<AppError>()

    assertEquals(ErrorReason.Authentication, error.reason)
    assertEquals("Invalid refresh token", error.description)
  }

  @Test
  fun refreshFailsIfCookieTokenNotValid() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/chonkit/refresh") {
        contentType(ContentType.Application.Json)
        cookie(
          name = "chonkit_refresh_token",
          value = "invalid",
          domain = "localhost",
          path = "/",
          maxAge = 120.minutes.toInt(DurationUnit.MINUTES),
          expires = Instant.now().plusSeconds(60).toGMTDate(),
          secure = true,
          httpOnly = true,
          extensions = mapOf(),
        )
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(401, response.status.value)

    val error = response.body<AppError>()

    assertEquals(ErrorReason.Authentication, error.reason)
    assertEquals("Invalid refresh token", error.description)
  }

  @Test
  fun refreshTokenFailsNoRefresh() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/chonkit/refresh") {
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(401, response.status.value)

    val error = response.body<AppError>()

    assertEquals(ErrorReason.Authentication, error.reason)
    assertEquals("No refresh token found", error.description)
  }

  @Test
  fun refreshingTokenInvalidatesPrevious() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val authResponse =
      client.post("/auth/chonkit/token") { header("Cookie", sessionCookie(chadSession.sessionId)) }

    val refreshToken = authResponse.body<ChonkitAuthentication>().refreshToken

    val refreshResponse =
      client.post("/auth/chonkit/refresh") {
        contentType(ContentType.Application.Json)
        setBody(ChonkitAuthenticationRequest(refreshToken = refreshToken))
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(200, refreshResponse.status.value)

    val refreshFailedResponse =
      client.post("/auth/chonkit/refresh") {
        contentType(ContentType.Application.Json)
        setBody(ChonkitAuthenticationRequest(refreshToken = refreshToken))
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(401, refreshFailedResponse.status.value)

    val error = refreshFailedResponse.body<AppError>()

    assertEquals(ErrorReason.Authentication, error.reason)
    assertEquals("Invalid refresh token", error.description)

    val validRefreshToken = refreshResponse.body<ChonkitAuthentication>().refreshToken

    val refreshSuccessResponse =
      client.post("/auth/chonkit/refresh") {
        contentType(ContentType.Application.Json)
        setBody(ChonkitAuthenticationRequest(refreshToken = validRefreshToken))
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(200, refreshSuccessResponse.status.value)
  }

  @Test
  fun regularUserCannotGetTokenPair() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.post("/auth/chonkit/token") {
        header("Cookie", sessionCookie(peasantSession.sessionId))
      }

    assertEquals(401, response.status.value)
  }

  @Test
  fun loggingOutInvalidatesTokens() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val authResponse =
      client.post("/auth/chonkit/token") { header("Cookie", sessionCookie(chadSession.sessionId)) }

    val refreshToken = authResponse.body<ChonkitAuthentication>().refreshToken

    val logoutResponse =
      client.post("/auth/chonkit/logout") {
        contentType(ContentType.Application.Json)
        setBody(ChonkitAuthenticationRequest(refreshToken = refreshToken))
        header("Cookie", sessionCookie(chadSession.sessionId))
      }

    assertEquals(204, logoutResponse.status.value)

    val cookies = logoutResponse.setCookie()

    for (cookie in cookies) {
      if (cookie.name != "kappi") assertEquals(0, cookie.maxAge)
    }
  }
}
