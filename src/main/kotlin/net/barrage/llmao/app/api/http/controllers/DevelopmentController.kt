package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAuthentication
import net.barrage.llmao.app.AdapterState
import net.barrage.llmao.app.api.http.CookieFactory
import net.barrage.llmao.app.api.http.dto.SessionCookie
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.plugins.pathUuid

fun Route.devController(
  authService: AuthenticationService,
  userService: UserService,
  adapters: AdapterState,
) {
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

      val user = userService.get(userId)
      call.sessions.set(SessionCookie(sessionId))

      val chonkitAuth =
        adapters.runIfEnabled<ChonkitAuthenticationService, ChonkitAuthentication?> { adapter ->
          if (user.role != Role.ADMIN) {
            return@runIfEnabled null
          }

          val chonkitAuth = adapter.authenticate(user)

          val accessCookie = CookieFactory.createChonkitAccessTokenCookie(chonkitAuth.accessToken)
          val refreshCookie =
            CookieFactory.createChonkitRefreshTokenCookie(chonkitAuth.refreshToken)

          call.response.cookies.append(accessCookie)
          call.response.cookies.append(refreshCookie)

          return@runIfEnabled chonkitAuth
        }

      call.respond(DevLoginResponse(sessionId, chonkitAuth))
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

/** Necessary because of KUUID treachery. */
@Serializable
data class DevLoginResponse(val sessionId: KUUID, val chonkit: ChonkitAuthentication?)
