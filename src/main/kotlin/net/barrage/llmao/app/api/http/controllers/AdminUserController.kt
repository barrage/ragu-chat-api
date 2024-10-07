package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.api.http.queryPagination
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.UpdateUserAdmin
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.plugins.pathUuid
import net.barrage.llmao.plugins.query

fun Route.adminUserRoutes(userService: UserService) {
  route("/admin/users") {
    get(adminGetAllUsers()) {
      val pagination = call.query(PaginationSort::class)
      val users = userService.getAll(pagination)
      call.respond(HttpStatusCode.OK, users)
    }

    post(createUser()) {
      val newUser: CreateUser = call.receive<CreateUser>()
      val user = userService.create(newUser)
      call.respond(HttpStatusCode.Created, user)
    }

    route("/{id}") {
      get(adminGetUser()) {
        val userId = call.pathUuid("id")
        val user = userService.get(userId)
        call.respond(HttpStatusCode.OK, user)
      }

      put(adminUpdateUser()) {
        val userId = call.pathUuid("id")
        val updateUser = call.receive<UpdateUserAdmin>()
        val user = userService.updateAdmin(userId, updateUser)
        call.respond(HttpStatusCode.OK, user)
      }

      delete(deleteUser()) {
        val userId = call.pathUuid("id")
        userService.delete(userId)
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }
}

// OpenAPI documentation
private fun adminGetAllUsers(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Retrieve list of all users"
  request { queryPagination() }
  response {
    HttpStatusCode.OK to
      {
        description = "List of all users"
        body<CountedList<User>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving users"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Retrieve user by ID"
  request {
    pathParameter<KUUID>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "User retrieved successfully"
        body<User>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving user"
        body<List<AppError>> {}
      }
  }
}

private fun createUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Create new user"
  request { body<CreateUser>() }
  response {
    HttpStatusCode.Created to
      {
        description = "User created successfully"
        body<User>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating user"
        body<List<AppError>> {}
      }
  }
}

private fun adminUpdateUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Update user"
  request {
    pathParameter<KUUID>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateUserAdmin>()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "User updated successfully"
        body<User>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating user"
        body<List<AppError>> {}
      }
  }
}

private fun deleteUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Delete user"
  request {
    pathParameter<KUUID>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "User deleted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting user"
        body<List<AppError>> {}
      }
  }
}
