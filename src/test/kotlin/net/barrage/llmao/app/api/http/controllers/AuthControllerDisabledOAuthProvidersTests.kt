package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AuthControllerDisabledOAuthProvidersTests :
  IntegrationTest(useWiremock = true, oAuthProviders = emptyList()) {
  lateinit var admin: User
  lateinit var adminSession: Session

  @BeforeAll
  fun setup() {
    runBlocking {
      admin = postgres.testUser(admin = true, active = true, email = "admin@barrage.net")
      adminSession = postgres.testSession(admin.id)
    }
  }

  @Test
  fun providersAreEmpty() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/providers") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }
    Assertions.assertEquals(200, response.status.value)
    val body = response.body<ProvidersResponse>()
    Assertions.assertEquals(0, body.auth.size)
  }

  @Test
  fun loginThrowsErrorWhenGoogleOAuthIsDisabled() = test {
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

    Assertions.assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.InvalidProvider, body.reason)
    Assertions.assertEquals("Unsupported auth provider 'google'", body.description)
  }

  @Test
  fun loginThrowsErrorWhenAppleOAuthIsDisabled() = test {
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

    Assertions.assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.InvalidProvider, body.reason)
    Assertions.assertEquals("Unsupported auth provider 'apple'", body.description)
  }

  @Test
  fun loginThrowsErrorWhenCarnetOAuthIsDisabled() = test {
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

    Assertions.assertEquals(400, response.status.value)
    val body = response.body<AppError>()
    Assertions.assertEquals(ErrorReason.InvalidProvider, body.reason)
    Assertions.assertEquals("Unsupported auth provider 'carnet'", body.description)
  }
}
