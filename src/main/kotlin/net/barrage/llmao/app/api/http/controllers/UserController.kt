package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.api.http.runWithImage
import net.barrage.llmao.app.api.http.user
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.error.AppError

fun Route.userRoutes(userService: UserService) {
  get("/users/current", getUser()) {
    val user = call.user()
    call.respond(user)
  }

  put("/users", userUpdate()) {
    val user = call.user()
    val updateUser = call.receive<UpdateUser>()
    val userUpdated = userService.updateUser(user.id, updateUser)
    call.respond(userUpdated)
  }

  route("users/avatars") {
    post(uploadUserAvatar()) {
      val user = call.user()
      call.runWithImage(user.id) { image ->
        userService.uploadUserAvatar(user.id, image)
        call.respond(HttpStatusCode.Created, image.name)
      }
    }

    delete(deleteUserAvatar()) {
      val user = call.user()
      userService.deleteUserAvatar(user.id)
      call.respond(HttpStatusCode.NoContent)
    }
  }
}

// OpenAPI documentation
private fun getUser(): OpenApiRoute.() -> Unit = {
  tags("users")
  description = "Retrieve logged in user"
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

private fun userUpdate(): OpenApiRoute.() -> Unit = {
  tags("users")
  description = "Update user"
  request { body<UpdateUser> {} }
  response {
    HttpStatusCode.OK to
      {
        description = "User updated"
        body<User> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating user"
        body<List<AppError>> {}
      }
  }
}

private fun uploadUserAvatar(): OpenApiRoute.() -> Unit = {
  tags("users/avatars")
  description = "Upload user avatar"
  request {
    body<ByteArray> {
      description = "Avatar image, .jpg or .png format"
      mediaTypes = setOf(ContentType.Image.JPEG, ContentType.Image.PNG)
      required = true
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "User with avatar"
        body<User> {}
      }
  }
}

private fun deleteUserAvatar(): OpenApiRoute.() -> Unit = {
  tags("users/avatars")
  description = "Delete user avatar"
  response { HttpStatusCode.NoContent to { description = "User avatar deleted successfully" } }
}
