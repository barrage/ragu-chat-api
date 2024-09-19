package net.barrage.llmao.controllers

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.models.UserContext
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.SessionService
import net.barrage.llmao.services.UserService

@Resource("users")
class UserController {
  @Resource("{id}") class User(val parent: UserController, val id: KUUID)
}

fun Route.userRoutes() {
  val userService = UserService()

  authenticate("auth-session") {
    get<UserController> {
      val currentUser = UserContext.currentUser
      val user: UserDto = userService.get(currentUser!!.id)
      call.respond(HttpStatusCode.OK, user)
      return@get
    }

    get("/auth/current") {
      val loggedInUser = UserContext.currentUser
      val sessionId = UserContext.sessionId!!
      val user: UserDto = userService.get(loggedInUser!!.id)
      val currentUser: CurrentUserDto = toCurrentUserDto(user, sessionId)
      call.respond(HttpStatusCode.OK, currentUser)
      return@get
    }

    put<UserController> {
      val currentUser = UserContext.currentUser
      val updateUser: UpdateUserDTO = call.receive<UpdateUserDTO>()
      val user: UserDto = userService.update(currentUser!!.id, updateUser)
      call.respond(HttpStatusCode.OK, user)
      return@put
    }
  }

  if (application.environment.config.property("ktor.environment").getString() == "development") {
    post<UserController> {
      val newUser: NewDevUserDTO = call.receive<NewDevUserDTO>()
      val user: UserDto = userService.createDev(newUser)
      call.respond(HttpStatusCode.OK, user)
      return@post
    }
  }

  if (application.environment.config.property("ktor.environment").getString() == "development") {
    post<UserController.User> {
      val sessionId = KUUID.randomUUID()
      call.sessions.set(UserSession(sessionId))
      SessionService().store(sessionId, it.id)

      val rawString = "app=%23n&id=%23s${sessionId}&source=%23n"
      val encodedString = URLEncoder.encode(rawString, StandardCharsets.UTF_8.toString())
      call.respond(HttpStatusCode.OK, encodedString)
      return@post
    }
  }
}
