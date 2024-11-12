package net.barrage.llmao.adapters.chonkit.api

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAccessTokenCookie
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAuthentication
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAuthenticationRequest
import net.barrage.llmao.adapters.chonkit.dto.ChonkitRefreshTokenCookie
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.queryParam
import net.barrage.llmao.plugins.user

fun Route.chonkitAuthRouter(service: ChonkitAuthenticationService) {
  route("/auth/chonkit") {
    post("/token", createChonkitToken()) {
      val user = call.user()

      val existingRefreshToken = call.sessions.get<ChonkitRefreshTokenCookie>()

      val chonkitAuth = service.authenticate(user, existingRefreshToken?.token)

      call.sessions.set(ChonkitAccessTokenCookie.from(chonkitAuth))
      call.sessions.set(ChonkitRefreshTokenCookie.from(chonkitAuth))

      call.respond(chonkitAuth)
    }

    post("/refresh", refreshChonkitToken()) {
      val user = call.user()
      var refreshToken: String

      try {
        val request = call.receive<ChonkitAuthenticationRequest>()
        refreshToken = request.refreshToken
      } catch (e: ContentTransformationException) {
        val refreshCookie =
          call.sessions.get<ChonkitRefreshTokenCookie>()
            ?: throw AppError.api(ErrorReason.Authentication, "No refresh token found")
        refreshToken = refreshCookie.token
      }

      val chonkitAuth = service.refresh(user.id, refreshToken)

      call.sessions.set(ChonkitAccessTokenCookie.from(chonkitAuth))
      call.sessions.set(ChonkitRefreshTokenCookie.from(chonkitAuth))

      call.respond(chonkitAuth)
    }

    post("/logout", logoutChonkitToken()) {
      val user = call.user()
      val purge = call.queryParam("purge")?.toBoolean() ?: false
      var refreshToken: String

      try {
        val request = call.receive<ChonkitAuthenticationRequest>()
        refreshToken = request.refreshToken
      } catch (e: ContentTransformationException) {
        val refreshCookie =
          call.sessions.get<ChonkitRefreshTokenCookie>()
            ?: throw AppError.api(ErrorReason.Authentication, "No refresh token found")
        refreshToken = refreshCookie.token
      }

      service.logout(user.id, refreshToken, purge)

      call.sessions.clear<ChonkitRefreshTokenCookie>()
      call.sessions.clear<ChonkitAccessTokenCookie>()

      call.respond(HttpStatusCode.NoContent)
    }
  }
}

private fun createChonkitToken(): OpenApiRoute.() -> Unit = {
  tags("auth", "chonkit")
  description = "Generate a Chonkit access token"
  summary = "Generate Chonkit token"
  response {
    HttpStatusCode.OK to
      {
        description = "Chonkit token generated"
        body<ChonkitAuthentication>()
      }
    HttpStatusCode.Unauthorized to
      {
        description = "Unauthorized"
        body<List<AppError>> {}
      }
  }
}

private fun refreshChonkitToken(): OpenApiRoute.() -> Unit = {
  tags("auth", "chonkit")
  description = "Refresh a Chonkit access token"
  summary = "Refresh Chonkit token"
  request { body<ChonkitAuthenticationRequest>() }
  response {
    HttpStatusCode.OK to
      {
        description = "Chonkit token refreshed"
        body<ChonkitAuthentication>()
      }
    HttpStatusCode.Unauthorized to
      {
        description = "Unauthorized"
        body<List<AppError>> {}
      }
  }
}

private fun logoutChonkitToken(): OpenApiRoute.() -> Unit = {
  tags("auth", "chonkit")
  description = "Logout Chonkit token"
  summary = "Revoke Chonkit token"
  request {
    queryParameter<Boolean>("purge") {
      description = "If given and true, purge all Chonkit tokens"
      required = false
      example("example") { value = false }
    }
    body<ChonkitAuthenticationRequest>()
  }
  response { HttpStatusCode.NoContent to { description = "Chonkit session deleted" } }
}
