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
import io.ktor.server.sessions.*
import net.barrage.llmao.dtos.users.NewDevUserDTO
import net.barrage.llmao.dtos.users.UpdateUserDTO
import net.barrage.llmao.dtos.users.UserDto
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.services.SessionService
import net.barrage.llmao.services.UserService

@Resource("users")
class UserController {}

fun Route.userRoutes() {
    val userService = UserService()

    authenticate("auth-session") {
        get<UserController> {
            val userSession = call.sessions.get<UserSession>()
            val userId = SessionService().get(userSession!!.id)?.userId!!
            val user: UserDto = userService.get(userId)
            call.respond(HttpStatusCode.OK, user)
            return@get
        }

        get("/auth/current") {
            val userSession = call.sessions.get<UserSession>()
            val userId = SessionService().get(userSession!!.id)?.userId!!
            val user: UserDto = userService.get(userId)
            call.respond(HttpStatusCode.OK, user)
            return@get
        }

        put<UserController> {
            val userSession = call.sessions.get<UserSession>()
            val userId = SessionService().get(userSession!!.id)?.userId!!
            val updateUser: UpdateUserDTO = call.receive<UpdateUserDTO>()
            val user: UserDto = userService.update(userId, updateUser)
            call.respond(HttpStatusCode.OK, user)
            return@put
        }
    }

    if (application.environment.config.property("ktor.environment").getString() == "development") {
        post<UserController> {
            val newUser: NewDevUserDTO = call.receive<NewDevUserDTO>()
            val user: UserDto = userService.createDev(newUser)
            call.respond(HttpStatusCode.OK, user)
            return@post
        }
    }
}