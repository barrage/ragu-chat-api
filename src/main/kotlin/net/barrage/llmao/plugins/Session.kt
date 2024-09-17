package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import net.barrage.llmao.enums.Roles
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.services.SessionService
import net.barrage.llmao.services.UserService

fun Application.configureSession() {
    install(Sessions) {
        cookie<UserSession>(this@configureSession.environment.config.property("session.cookieName").getString()) {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 24 * 60 * 60 * 1 // 1 day
            cookie.httpOnly =
                this@configureSession.environment.config.property("session.httpOnly").getString().toBoolean()
            cookie.secure = this@configureSession.environment.config.property("session.secure").getString().toBoolean()
            cookie.domain = this@configureSession.environment.config.property("session.domain").getString()
            cookie.extensions["SameSite"] = "Lax"
        }
    }

    authentication {
        session<UserSession>("auth-session") {
            validate { session ->
                if (session.id.toString().isNotEmpty()) {
                    val serverSession = SessionService().get(session.id)
                    if (serverSession != null && serverSession.isValid()) {
                        val user = UserService().get(serverSession.userId)
                        if (user.active) {
                            session
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
            }
        }

        session<UserSession>("auth-session-admin") {
            validate { session ->
                if (session.id.toString().isNotEmpty()) {
                    val serverSession = SessionService().get(session.id)
                    if (serverSession != null && serverSession.isValid()) {
                        val user = UserService().get(serverSession.userId)
                        if (user.active && user.role == Roles.ADMIN.name) {
                            session
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
            }
        }
    }
}

fun Application.extendSession() {
    intercept(Plugins) {
        val userSession = call.sessions.get<UserSession>()
        if (userSession != null) {
            val sessionService = SessionService()
            val serverSession = sessionService.get(userSession.id)
            if (serverSession != null && serverSession.isValid()) {
                sessionService.extend(serverSession.sessionId)
                call.sessions.set(UserSession(userSession.id))
            } else {
                call.sessions.clear<UserSession>()
            }
        }
    }
}