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
import net.barrage.llmao.app.api.http.dto.SearchFiltersAdminUsersQuery
import net.barrage.llmao.app.api.http.queryListUsersFilters
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.CsvImportUsersResult
import net.barrage.llmao.core.models.UpdateUserAdmin
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.pathUuid
import net.barrage.llmao.plugins.query
import net.barrage.llmao.plugins.user
import net.barrage.llmao.string

fun Route.adminUserRoutes(userService: UserService) {
  route("/admin/users") {
    get(adminGetAllUsers()) {
      val pagination = call.query(PaginationSort::class)
      val filters = call.query(SearchFiltersAdminUsersQuery::class).toSearchFiltersAdminUsers()
      val users = userService.getAll(pagination, filters)
      call.respond(HttpStatusCode.OK, users)
    }

    post(createUser()) {
      val newUser: CreateUser = call.receive<CreateUser>()
      val user = userService.create(newUser)
      call.respond(HttpStatusCode.Created, user)
    }

    post("/import-csv", importUsersCsv()) {
      if (!call.request.contentType().match(ContentType.Text.CSV)) {
        throw AppError.api(ErrorReason.InvalidContentType, "Expected type: text/csv")
      }

      val contentLength =
        call.request.contentLength()
          ?: throw AppError.api(ErrorReason.InvalidParameter, "Expected content in request body")
      val maxContentLength =
        application.environment.config.string("upload.csv.maxFileSize").toLong()

      if (contentLength > maxContentLength) {
        throw AppError.api(
          ErrorReason.PayloadTooLarge,
          "CSV file size exceeds the maximum allowed size",
        )
      }

      val csv = call.receiveChannel()
      val results = userService.importUsersCsv(csv)

      call.respond(HttpStatusCode.OK, results)
      return@post
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
        val loggedInUser = call.user()
        val user = userService.updateAdmin(userId, updateUser, loggedInUser.id)
        call.respond(HttpStatusCode.OK, user)
      }

      delete(deleteUser()) {
        val userId = call.pathUuid("id")
        val loggedInUser = call.user()
        userService.delete(userId, loggedInUser.id)
        call.respond(HttpStatusCode.NoContent)
      }

      route("/avatars") {
        post(uploadUserAvatar()) {
          val userId = call.pathUuid("id")
          val fileExtension =
            when (call.request.contentType()) {
              ContentType.Image.JPEG -> "jpg"
              ContentType.Image.PNG -> "png"
              else ->
                throw AppError.api(
                  ErrorReason.InvalidContentType,
                  "Expected type: image/jpeg or image/png",
                )
            }

          val contentLength =
            call.request.contentLength()
              ?: throw AppError.api(
                ErrorReason.InvalidParameter,
                "Expected content in request body",
              )
          if (
            contentLength >
              application.environment.config.string("upload.image.maxFileSize").toLong()
          ) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            return@post
          }

          val avatar = call.receiveChannel()
          val userUpdated = userService.setUserAvatar(userId, fileExtension, avatar)
          call.respond(userUpdated)
        }

        delete(deleteUserAvatar()) {
          val userId = call.pathUuid("id")
          userService.deleteUserAvatar(userId)
          call.respond(HttpStatusCode.NoContent)
        }
      }
    }
  }
}

// OpenAPI documentation
private fun adminGetAllUsers(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Retrieve list of all users"
  request {
    queryPaginationSort()
    queryListUsersFilters()
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

private fun importUsersCsv(): OpenApiRoute.() -> Unit = {
  tags("admin/users")
  description = "Import users from CSV"
  request {
    body<String> {
      description = "Users to import in CSV format"
      required = true
      mediaTypes = setOf(ContentType.Text.CSV)
      example("default") {
        value =
          """
            FullName,FirstName,LastName,Email,Role
            John Doe,John,Doe,john.doe@example.com,user
          """
            .trimIndent()
      }
    }
  }
  response {
    HttpStatusCode.Created to
      {
        description = "Users imported successfully"
        body<CsvImportUsersResult>()
      }
    HttpStatusCode.BadRequest to
      {
        description = "Failed to import users or invalid CSV"
        body<List<AppError>> {}
      }
  }
}

private fun uploadUserAvatar(): OpenApiRoute.() -> Unit = {
  tags("admin/users/avatars")
  description = "Upload user avatar"
  request {
    pathParameter<KUUID>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
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
  tags("admin/users/avatars")
  description = "Delete user avatar"
  request {
    pathParameter<KUUID>("id") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response { HttpStatusCode.NoContent to { description = "User avatar deleted successfully" } }
}
