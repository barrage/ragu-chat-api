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
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.plugins.pathUuid

fun Route.devController(authService: AuthenticationService, userService: UserService) {
  route("/dev") {
    post("/users", devCreateUser()) {
      val newUser = call.receive<CreateUser>()
      val user = userService.create(newUser)
      call.respond(user)
    }

    post("/auth/login/{id}", devLoginUser()) {
      val sessionId = KUUID.randomUUID()
      val userId = call.pathUuid("id")
      authService.store(sessionId, userId)

      call.sessions.set(SessionCookie(sessionId))

      call.respond("$sessionId")
    }
  }
}

fun devCreateUser(): OpenApiRoute.() -> Unit = {
  summary = "Create user"
  description = "Create a new user."
  tags("dev")
  request { body<CreateUser>() }
  response {
    HttpStatusCode.OK to
      {
        description = "User created successfully."
        this.body<User> {}
      }
  }
}

fun devLoginUser(): OpenApiRoute.() -> Unit = {
  summary = "Login user"
  description = "Login a user, dev route."
  tags("dev")
  request { pathParameter<KUUID>("id") }
  response {
    HttpStatusCode.OK to
      {
        description = "User logged in successfully."
        this.body<String> {}
      }
  }
}
