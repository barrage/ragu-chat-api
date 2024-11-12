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
import net.barrage.llmao.app.api.http.dto.SessionCookie
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

fun Route.authRoutes(service: AuthenticationService) {
  post("/auth/login", loginUser()) {
    val form = call.receiveParameters()
    val loginPayload = LoginPayload.fromForm(form)

    val session = service.authenticateUser(loginPayload)
    call.sessions.set(SessionCookie(session.sessionId))

    call.respond(HttpStatusCode.NoContent)
  }

  post("/auth/logout", logoutUser()) {
    val userSession = call.sessions.get<SessionCookie>()

    if (userSession == null) {
      call.respond(HttpStatusCode.NoContent)
      return@post
    }

    service.logout(userSession.id)

    call.sessions.clear<SessionCookie>()

    call.respond(HttpStatusCode.NoContent)
  }

  post("/auth/apple/callback-web", callbackWeb()) {
    val code =
      call.receiveParameters()["code"]
        ?: throw AppError.api(ErrorReason.InvalidParameter, "Missing code")

    call.respondRedirect(
      url = "${application.environment.config.string("frontend.url")}/auth/apple?code=$code",
      permanent = true,
    )
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
    }
  }
  response {
    HttpStatusCode.OK to { description = "User logged in." }
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
  summary = "Apple OAuth callback for web"
  description = "Apple OAuth callback for web."
  tags("auth")
  request { multipartBody { part<String>("code") {} } }
  response {
    HttpStatusCode.OK to { description = "User logged in." }
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
