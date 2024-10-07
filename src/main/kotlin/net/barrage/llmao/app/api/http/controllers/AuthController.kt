package net.barrage.llmao.app.api.http.controllers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.barrage.llmao.app.api.http.dto.SessionCookie
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.services.AuthenticationService

fun Route.authRoutes(service: AuthenticationService) {
  post("/auth/login") {
    val form = call.receiveParameters()
    val loginPayload = LoginPayload.fromForm(form)

    val session = service.authenticateUser(loginPayload)
    call.sessions.set(SessionCookie(session.sessionId))

    call.respond(HttpStatusCode.OK)
  }

  post("/auth/logout") {
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
