package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAuthentication
import net.barrage.llmao.app.AdapterState
import net.barrage.llmao.app.api.http.CookieFactory
import net.barrage.llmao.app.api.http.dto.SessionCookie
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

fun Route.authRoutes(service: AuthenticationService, adapters: AdapterState) {
  post("/auth/login", loginUser()) {
    val form = call.receiveParameters()
    val loginPayload = LoginPayload.fromForm(form)

    val authenticationResult = service.authenticateUser(loginPayload)

    call.sessions.set(SessionCookie(authenticationResult.session.sessionId))

    if (authenticationResult.userInfo.picture != null) {
      call.response.cookies.append(
        CookieFactory.createUserPictureCookie(authenticationResult.userInfo.picture)
      )
    }

    // Chonkit authorization

    val chonkitTokens =
      adapters.runIfEnabled<ChonkitAuthenticationService, ChonkitAuthentication?> { adapter ->
        if (authenticationResult.user.role != Role.ADMIN) {
          return@runIfEnabled null
        }

        val existingRefreshToken =
          call.request.cookies[CookieFactory.getRefreshTokenCookieName(), CookieEncoding.RAW]
        val chonkitAuth = adapter.authenticate(authenticationResult.user, existingRefreshToken)

        val accessCookie = CookieFactory.createChonkitAccessTokenCookie(chonkitAuth.accessToken)
        val refreshCookie = CookieFactory.createChonkitRefreshTokenCookie(chonkitAuth.refreshToken)

        call.response.cookies.append(accessCookie)
        call.response.cookies.append(refreshCookie)

        return@runIfEnabled chonkitAuth
      }

    call.respond(chonkitTokens ?: HttpStatusCode.NoContent)
  }

  post("/auth/logout", logoutUser()) {
    val userSession = call.sessions.get<SessionCookie>()

    if (userSession == null) {
      call.respond(HttpStatusCode.NoContent)
      return@post
    }

    service.logout(userSession.id)

    call.sessions.clear<SessionCookie>()

    call.response.cookies.append(CookieFactory.createUserPictureExpiryCookie())

    val user =
      service.getUserForSession(userSession.id)
        ?: let {
          call.respond(HttpStatusCode.NoContent)
          return@post
        }

    adapters.runIfEnabled<ChonkitAuthenticationService, Unit> { adapter ->
      val refreshToken =
        call.request.cookies[CookieFactory.getRefreshTokenCookieName(), CookieEncoding.RAW]
          ?: return@runIfEnabled

      adapter.logout(user.id, refreshToken, false)

      val accessExpiryCookie = CookieFactory.createChonkitAccessTokenExpiryCookie()
      val refreshExpiryCookie = CookieFactory.createChonkitRefreshTokenExpiryCookie()

      call.response.cookies.append(accessExpiryCookie)
      call.response.cookies.append(refreshExpiryCookie)
    }

    call.respond(HttpStatusCode.NoContent)
  }

  if (
    application.environment.config.tryGetString("ktor.features.oauth.apple")?.toBoolean() == true
  ) {
    post("/auth/apple/callback-web", callbackWeb()) {
      val code =
        call.receiveParameters()["code"]
          ?: throw AppError.api(ErrorReason.InvalidParameter, "Missing code")

      call.response.headers.append(
        "Location",
        application.environment.config.string("oauth.apple.frontendUrl") + "/auth/apple?code=$code",
      )

      call.respond(HttpStatusCode.PermanentRedirect)
    }
  }
}

fun loginUser(): OpenApiRoute.() -> Unit = {
  summary = "Login user"
  description = "Login a user via OAuth."
  tags("auth")
  securitySchemeNames = listOf()
  request {
    multipartBody {
      part<String>("code") {}
      part<String>("grant_type") {}
      part<String>("redirect_uri") {}
      part<String>("provider") {}
      part<String>("source") {}
      part<String>("code_verifier") {}
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Administrator logged in."
        body<ChonkitAuthentication>()
      }
    HttpStatusCode.NotFound to { description = "User logged in." }
    HttpStatusCode.BadRequest to
      {
        description = "Invalid login data."
        body<List<AppError>>()
      }
    HttpStatusCode.NotFound to
      {
        description = "User not found."
        body<List<AppError>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Error logging in user."
        body<List<AppError>>()
      }
  }
}

fun logoutUser(): OpenApiRoute.() -> Unit = {
  summary = "Logout user"
  description = "Logout a user."
  tags("auth")
  response {
    HttpStatusCode.OK to { description = "User logged out." }
    HttpStatusCode.Unauthorized to
      {
        description = "User not logged in."
        body<List<AppError>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Error logging out user."
        body<List<AppError>>()
      }
  }
}

private fun callbackWeb(): OpenApiRoute.() -> Unit = {
  summary = "Apple OAuth callback for web."
  description = "Apple OAuth callback for web."
  tags("auth")
  request { multipartBody { part<String>("code") {} } }
  response {
    HttpStatusCode.PermanentRedirect to { description = "Redirecting to frontend." }
    HttpStatusCode.BadRequest to
      {
        description = "Invalid login data."
        body<List<AppError>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Error logging in user."
        body<List<AppError>>()
      }
    HttpStatusCode.Unauthorized to
      {
        description = "Unauthorized."
        body<List<AppError>>()
      }
  }
}

@Serializable data class AppleAppSiteAssociation(val applinks: AppLinks)

@Serializable
data class AppLinks(val apps: List<String> = emptyList(), val details: List<AppDetail>)

@Serializable data class AppDetail(val appID: String, val paths: List<String>)

@Serializable data class AssetLink(val relation: List<String>, val target: AssetLinkTarget)

@Serializable
data class AssetLinkTarget(
  val namespace: String,
  val packageName: String,
  @SerialName("sha256_cert_fingerprints") val sha256CertFingerprints: List<String>,
)
