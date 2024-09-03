package net.barrage.llmao.controllers

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.serializers.KUUIDSerializer
import net.barrage.llmao.services.UserService
import java.util.*

@Resource("users")
class UserController {
    @Resource("{id}")
    class User(
        val parent: UserController = UserController(),
        @Serializable(with = KUUIDSerializer::class)
        val id: UUID
    ) {
        @Resource("password")
        class Password(val parent: User)

        @Resource("role")
        class Role(val parent: User)

        @Resource("activate")
        class Activate(val parent: User)

        @Resource("deactivate")
        class Deactivate(val parent: User)
    }
}

fun Route.userRoutes() {
    val userService = UserService()

    get<UserController> {
        val users: List<UserDto> = userService.getAll()
        call.respond(HttpStatusCode.OK, users)
        return@get
    }

    post<UserController> {
        val newUser: NewUserDTO = call.receive<NewUserDTO>()
        val user: UserDto = userService.create(newUser)
        call.respond(HttpStatusCode.Created, user)
        return@post
    }

    get<UserController.User> {
        val user: UserDto = userService.get(it.id)
        call.respond(HttpStatusCode.OK, user)
        return@get
    }

    put<UserController.User> {
        val updateUser: UpdateUserDTO = call.receive<UpdateUserDTO>()
        val user: UserDto = userService.update(it.id, updateUser)
        call.respond(HttpStatusCode.OK, user)
        return@put
    }

    delete<UserController.User> {
        userService.delete(it.id)
        call.respond(HttpStatusCode.NoContent)
        return@delete
    }

    put<UserController.User.Password> {
        val updatePassword: UpdateUserPasswordDTO = call.receive<UpdateUserPasswordDTO>()
        val user: UserDto = userService.updatePassword(it.parent.id, updatePassword)
        call.respond(HttpStatusCode.OK, user)
        return@put
    }

    put<UserController.User.Role> {
        val updateRole: UpdateUserRoleDTO = call.receive<UpdateUserRoleDTO>()
        val user: UserDto = userService.updateRole(it.parent.id, updateRole)
        call.respond(HttpStatusCode.OK, user)
        return@put
    }

    put<UserController.User.Activate> {
        val user: UserDto = userService.activate(it.parent.id)
        call.respond(HttpStatusCode.OK, user)
        return@put
    }

    delete<UserController.User.Deactivate> {
        val user: UserDto = userService.deactivate(it.parent.id)
        call.respond(HttpStatusCode.OK, user)
        return@delete
    }
}