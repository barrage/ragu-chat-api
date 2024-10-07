package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.barrage.llmao.app.api.http.dto.SessionCookie
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.plugins.pathUuid

fun Route.devController(authService: AuthenticationService, userService: UserService) {
  route("/dev") {
    post("/users") {
      val newUser = call.receive<CreateUser>()
      val user = userService.create(newUser)
      call.respond(user)
    }

    post("/auth/login/{id}") {
      val sessionId = KUUID.randomUUID()
      val userId = call.pathUuid("id")
      authService.store(sessionId, userId)

      call.sessions.set(SessionCookie(sessionId))

      call.respond("$sessionId")
    }
  }
}
