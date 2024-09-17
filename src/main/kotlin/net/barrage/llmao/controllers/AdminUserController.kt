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
import net.barrage.llmao.dtos.users.AdminUpdateUserDTO
import net.barrage.llmao.dtos.users.NewUserDTO
import net.barrage.llmao.dtos.users.UpdateUserRoleDTO
import net.barrage.llmao.dtos.users.UserDto
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.UserService

@Resource("admin/users")
class AdminUserController {
    @Resource("{id}")
    class User(
        val parent: AdminUserController,
        val id: KUUID
    ) {
        @Resource("role")
        class Role(val parent: User)

        @Resource("activate")
        class Activate(val parent: User)

        @Resource("deactivate")
        class Deactivate(val parent: User)
    }
}

fun Route.adminUserRoutes() {
    val userService = UserService()

    authenticate("auth-session-admin") {
        get<AdminUserController> {
            val users: List<UserDto> = userService.getAll()
            call.respond(HttpStatusCode.OK, users)
            return@get
        }

        get<AdminUserController.User> {
            val user: UserDto = userService.get(it.id)
            call.respond(HttpStatusCode.OK, user)
            return@get
        }

        post<AdminUserController> {
            val newUser: NewUserDTO = call.receive<NewUserDTO>()
            val user: UserDto = userService.create(newUser)
            call.respond(HttpStatusCode.Created, user)
            return@post
        }

        put<AdminUserController.User> {
            val updateUser: AdminUpdateUserDTO = call.receive<AdminUpdateUserDTO>()
            val user: UserDto = userService.update(it.id, updateUser)
            call.respond(HttpStatusCode.OK, user)
            return@put
        }

        put<AdminUserController.User.Role> {
            val updateRole: UpdateUserRoleDTO = call.receive<UpdateUserRoleDTO>()
            val user: UserDto = userService.updateRole(it.parent.id, updateRole)
            call.respond(HttpStatusCode.OK, user)
            return@put
        }

        put<AdminUserController.User.Activate> {
            val user: UserDto = userService.activate(it.parent.id)
            call.respond(HttpStatusCode.OK, user)
            return@put
        }

        put<AdminUserController.User.Deactivate> {
            val user: UserDto = userService.deactivate(it.parent.id)
            call.respond(HttpStatusCode.OK, user)
            return@put
        }

        delete<AdminUserController.User> {
            userService.delete(it.id)
            call.respond(HttpStatusCode.NoContent)
            return@delete
        }
    }
}