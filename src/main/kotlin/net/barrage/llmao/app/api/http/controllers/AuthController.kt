package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
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
}

fun loginUser(): OpenApiRoute.() -> Unit = {
  summary = "Login user"
  description = "Login a user with data gotten from oAuth service."
  tags("auth")
  securitySchemeNames = listOf()
  request { body<LoginPayload>() }
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
