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
import net.barrage.llmao.dtos.PaginationInfo
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.error.Error
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.UserService

@Resource("admin/users")
class AdminUserController(
  val page: Int? = 1,
  val size: Int? = 10,
  val sortBy: String? = "lastName",
  val sortOrder: String? = "asc",
) {
  @Resource("{id}")
  class User(val parent: AdminUserController, val id: KUUID) {
    @Resource("activate") class Activate(val parent: User)

    @Resource("deactivate") class Deactivate(val parent: User)
  }
}

fun Route.adminUserRoutes() {
  val userService = UserService()

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
      val page = it.page ?: 1
      val size = it.size ?: 10
      val sortBy = it.sortBy ?: "lastName"
      val sortOrder = it.sortOrder ?: "asc"

      val users: UserResponse = userService.getAll(page, size, sortBy, sortOrder)
      val response =
        toPaginatedUserDTO(users.users, PaginationInfo(users.count, page, size, sortBy, sortOrder))
      call.respond(HttpStatusCode.OK, response)
      return@get
    }

    get<AdminUserController.User>(adminGetUser()) {
      val user: UserDTO = userService.get(it.id)
      call.respond(HttpStatusCode.OK, user)
    }

    post<AdminUserController> {
      val newUser: CreateUser = call.receive<CreateUser>()
      val user: User = userService.create(newUser)
      call.respond(HttpStatusCode.Created, user)
    }

    put<AdminUserController.User> {
      val updateUser = call.receive<UpdateUserAdmin>()
      val user: User = userService.updateAdmin(it.id, updateUser)
      call.respond(HttpStatusCode.OK, user)
    }

    put<AdminUserController.User.Activate> {
      userService.setActiveStatus(it.parent.id, true)
      call.respond(HttpStatusCode.OK)
    }

    put<AdminUserController.User.Deactivate> {
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
        body<PaginatedUserDTO>()
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
        body<UserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving user"
        body<List<Error>> {}
      }
  }
}

fun createUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Create new user"
  request { body<NewUserDTO>() }
  response {
    HttpStatusCode.Created to
      {
        description = "User created successfully"
        body<UserDTO>()
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
    body<AdminUpdateUserDTO>()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "User updated successfully"
        body<UserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating user"
        body<List<Error>> {}
      }
  }
}

fun updateRole(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Update user role"
  request {
    pathParameter<String>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateUserRoleDTO>()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "User role updated successfully"
        body<UserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating user role"
        body<List<Error>> {}
      }
  }
}

fun activateUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Activate user"
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
        body<UserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while activating user"
        body<List<Error>> {}
      }
  }
}

fun deactivateUser(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Deactivate user"
  request {
    pathParameter<String>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "User deactivated successfully"
        body<UserDTO>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deactivating user"
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
