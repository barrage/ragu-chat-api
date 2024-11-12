package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.api.http.dto.SessionCookie
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.error.AppError

fun Route.authRoutes(service: AuthenticationService) {
  post("/auth/login", loginUser()) {
    val form = call.receiveParameters()
    val loginPayload = LoginPayload.fromForm(form)

    val session = service.authenticateUser(loginPayload)
    call.sessions.set(SessionCookie(session.sessionId))

    call.respond(HttpStatusCode.OK)
  }

  post("/auth/logout", logoutUser()) {
    val userSession = call.sessions.get<SessionCookie>()

    if (userSession == null) {
      call.respond(HttpStatusCode.OK)
      return@post
    }

    service.logout(userSession.id)

    call.sessions.clear<SessionCookie>()

    call.respond(HttpStatusCode.OK)
  }

  get("/apple-app-site-association") {
    val appleAppSiteAssociation =
      AppleAppSiteAssociation(
        applinks =
          AppLinks(
            details =
              listOf(
                AppDetail(
                  appID = application.environment.config.property("apple.appID").getString(),
                  paths = listOf("/oauthredirect"),
                ),
                AppDetail(
                  appID =
                    application.environment.config.property("multiplatform.ios.appID").getString(),
                  paths = listOf("/oauthredirect"),
                ),
              )
          )
      )

    val jsonString = Json.encodeToString(appleAppSiteAssociation)
    call.respond(HttpStatusCode.OK, jsonString)
  }

  get("/.well-known/assetlinks.json") {
    val assetLinks =
      listOf(
        AssetLink(
          relation = listOf("delegate_permission/common.handle_all_urls"),
          target =
            AssetLinkTarget(
              namespace = application.environment.config.property("android.namespace").getString(),
              packageName =
                application.environment.config.property("android.packageName").getString(),
              sha256CertFingerprints =
                application.environment.config.property("android.sha256CertFingerprints").getList(),
            ),
        )
      )

    call.respond(HttpStatusCode.OK, Json.encodeToString(assetLinks))
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
