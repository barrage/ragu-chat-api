package net.barrage.llmao.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.get
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.post
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.put
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.dtos.users.UpdateUserAdmin
import net.barrage.llmao.error.Error
import net.barrage.llmao.models.CountedList
import net.barrage.llmao.models.CreateUser
import net.barrage.llmao.models.PaginationSort
import net.barrage.llmao.models.User

@Resource("admin/users")
class AdminUserController(val pagination: PaginationSort = PaginationSort()) {
  @Resource("{id}")
  class User(val parent: AdminUserController, val id: KUUID) {
    @Resource("activate") class Activate(val parent: User)

    @Resource("deactivate") class Deactivate(val parent: User)
  }
}

fun Route.adminUserRoutes(userService: UserService) {
  // Unprotected routes for that sweet development efficiency
  if (application.environment.config.property("ktor.environment").getString() == "development") {
    post("/dev/users") {
      val newUser = call.receive<CreateUser>()
      val user = userService.create(newUser)
      call.respond(user)
    }
  }

  authenticate("auth-session-admin") {
    get<AdminUserController>(adminGetAllUsers()) {
      print(it.pagination)
      val users = userService.getAll(it.pagination)
      call.respond(HttpStatusCode.OK, users)
    }

    get<AdminUserController.User>(adminGetUser()) {
      val user = userService.get(it.id)
      call.respond(HttpStatusCode.OK, user)
    }

    post<AdminUserController>(createUser()) {
      val newUser: CreateUser = call.receive<CreateUser>()
      val user = userService.create(newUser)
      call.respond(HttpStatusCode.Created, user)
    }

    put<AdminUserController.User>(adminUpdateUser()) {
      val updateUser = call.receive<UpdateUserAdmin>()
      val user = userService.updateAdmin(it.id, updateUser)
      call.respond(HttpStatusCode.OK, user)
    }

    put<AdminUserController.User.Activate>(setActiveStatus()) {
      userService.setActiveStatus(it.parent.id, true)
      call.respond(HttpStatusCode.OK)
    }

    put<AdminUserController.User.Deactivate>(setActiveStatus()) {
      userService.setActiveStatus(it.parent.id, false)
      call.respond(HttpStatusCode.OK)
    }

    delete<AdminUserController.User>(deleteUser()) {
      userService.delete(it.id)
      call.respond(HttpStatusCode.NoContent)
    }
  }
}

// OpenAPI documentation
fun adminGetAllUsers(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Retrieve list of all users"
  request {
    queryParameter<Int>("page") {
      description = "Page number for pagination"
      required = false
      example("default") { value = 1 }
    }
    queryParameter<Int>("size") {
      description = "Number of items per page"
      required = false
      example("default") { value = 10 }
    }
    queryParameter<String>("sortBy") {
      description = "Sort by field"
      required = false
      example("default") { value = "lastName" }
    }
    queryParameter<String>("sortOrder") {
      description = "Sort order (asc or desc)"
      required = false
      example("default") { value = "asc" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "List of all users"
        body<CountedList<User>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving users"
        body<List<Error>> {}
      }
  }
}

fun adminGetUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Retrieve user by ID"
  request {
    pathParameter<String>("id") {
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
        body<List<Error>> {}
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
        body<List<Error>> {}
      }
  }
}

fun adminUpdateUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Update user"
  request {
    pathParameter<String>("id") {
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
        body<List<Error>> {}
      }
  }
}

fun setActiveStatus(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Set user active status"
  request {
    pathParameter<String>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "User activated successfully"
        body<User>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while activating user"
        body<List<Error>> {}
      }
  }
}

fun deleteUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Delete user"
  request {
    pathParameter<String>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "User deleted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting user"
        body<List<Error>> {}
      }
  }
}
