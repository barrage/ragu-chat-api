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
import io.ktor.server.resources.*
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.error.Error
import net.barrage.llmao.models.UserContext
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.plugins.sessionId
import net.barrage.llmao.services.SessionService
import net.barrage.llmao.services.UserService

fun Route.userRoutes() {
  val userService = UserService()

  authenticate("auth-session") {
    get("/users/current") {
      val sessionId = call.sessionId()
      val userId = SessionService().get(sessionId)?.userId!!

      val user = userService.get(userId)

      call.respond(user)
    }

    put("/users/current") {
      val userSession = call.sessions.get<UserSession>()
      val userId = SessionService().get(userSession!!.id)?.userId!!
      val updateUser: UpdateUser = call.receive<UpdateUser>()

      val user = userService.updateUser(userId, updateUser)

      call.respond(user)
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
