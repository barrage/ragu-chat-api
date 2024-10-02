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
import net.barrage.llmao.core.types.KUUID

fun Route.authRoutes(service: AuthenticationService) {
  post("/auth/login") {
    val form = call.receiveParameters()
    val loginPayload = LoginPayload.fromForm(form)

    val session = service.authenticateUser(loginPayload)
    call.sessions.set(SessionCookie(session.sessionId))

    call.respond(HttpStatusCode.OK)
  }

  if (application.environment.config.property("ktor.environment").getString() == "development") {
    post("/dev/auth/login/{id}") {
      val sessionId = KUUID.randomUUID()
      val userId = KUUID.fromString(call.parameters["id"])
      service.store(sessionId, userId)

      call.sessions.set(SessionCookie(sessionId))

      call.respond("$sessionId")
    }
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
