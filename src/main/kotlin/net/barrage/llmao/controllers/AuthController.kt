package net.barrage.llmao.controllers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.dtos.auth.LoginPayload
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.services.SessionService

fun Route.authRoutes(service: AuthenticationService) {
  post("/auth/login") {
    val form = call.receiveParameters()
    val loginPayload = LoginPayload.fromForm(form)

    val session = service.authenticateUser(loginPayload)
    call.sessions.set(UserSession(session.sessionId))

    call.respond(HttpStatusCode.OK)
  }

  post("/auth/logout") {
    val userSession = call.sessions.get<UserSession>()

    if (userSession == null) {
      call.respond(HttpStatusCode.OK)
      return@post
    }

    val serverSession = SessionService().get(userSession.id)

    if (serverSession != null && serverSession.isValid()) {
      SessionService().expire(serverSession.sessionId)
    }

    call.sessions.clear<UserSession>()
    call.respond(HttpStatusCode.OK)
  }
}
