package net.barrage.llmao.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.get
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.put
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.error.Error
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
    get<UserController>(getUser()) {
      val currentUser = UserContext.currentUser
      val user: UserDTO = userService.get(currentUser!!.id)
      call.respond(HttpStatusCode.OK, user)
      return@get
    }

    // TODO: move to auth controller
    get("/auth/current", getCurrent()) {
      val loggedInUser = UserContext.currentUser
      val sessionId = UserContext.sessionId!!
      val user: UserDTO = userService.get(loggedInUser!!.id)
      val currentUser: CurrentUserDTO = toCurrentUserDTO(user, sessionId)
      call.respond(HttpStatusCode.OK, currentUser)
      return@get
    }

    put<UserController>(updateUser()) {
      val currentUser = UserContext.currentUser
      val updateUser: UpdateUserDTO = call.receive<UpdateUserDTO>()
      val user: UserDTO = userService.update(currentUser!!.id, updateUser)
      call.respond(HttpStatusCode.OK, user)
      return@put
    }
  }

  if (application.environment.config.property("ktor.environment").getString() == "development") {
    post<UserController>({
      tags("users")
      description = "Create new user (development only)"
      request { body<NewUserDTO>() }
      response {
        HttpStatusCode.OK to
          {
            description = "Return created user"
            body<UserDTO>()
          }
        HttpStatusCode.InternalServerError to
          {
            description = "Internal server error occurred while retrieving user"
            body<List<Error>> {}
          }
      }
    }) {
      val newUser: NewUserDTO = call.receive<NewUserDTO>()
      val user: UserDTO = userService.create(newUser)
      call.respond(HttpStatusCode.Created, user)
      return@post
    }
  }

  if (application.environment.config.property("ktor.environment").getString() == "development") {
    post<UserController.User>({
      tags("users")
      description = "Create session for new user (development only)"
      request {
        pathParameter<String>("id") {
          description = "The ID of the user"
          example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
        }
      }
      response {
        HttpStatusCode.OK to
          {
            description = "Return created sessionId string"
            body<String>()
          }
        HttpStatusCode.InternalServerError to
          {
            description = "Internal server error occurred while retrieving user"
            body<List<Error>> {}
          }
      }
    }) {
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

// OpenAPI documentation
fun getUser(): OpenApiRoute.() -> Unit = {
  tags("users")
  description = "Retrieve logged in user"
  request {}
  response {
    HttpStatusCode.OK to
      {
        description = "List logged in user"
        body<UserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving user"
        body<List<Error>> {}
      }
  }
}

fun getCurrent(): OpenApiRoute.() -> Unit = {
  description = "Retrieve logged in user"
  request {}
  response {
    HttpStatusCode.OK to
      {
        description = "List logged in user"
        body<CurrentUserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving user"
        body<List<Error>> {}
      }
  }
}

fun updateUser(): OpenApiRoute.() -> Unit = {
  tags("users")
  description = "Update logged in user"
  request {}
  response {
    HttpStatusCode.OK to
      {
        description = "Return updated user"
        body<UserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving user"
        body<List<Error>> {}
      }
  }
}
