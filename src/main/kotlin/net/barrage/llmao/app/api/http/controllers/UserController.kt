package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.error.AppError
import net.barrage.llmao.plugins.user

fun Route.userRoutes(userService: UserService) {
  authenticate("auth-session") {
    get("/users/current", getUser()) { call.respond(call.user()) }

    put("/users") {
      val user = call.user()

      val updateUser = call.receive<UpdateUser>()

      val userUpdated = userService.updateUser(user.id, updateUser)

      call.respond(userUpdated)
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
        description = "Get logged in user"
        body<User>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving user"
        body<List<AppError>> {}
      }
  }
}
