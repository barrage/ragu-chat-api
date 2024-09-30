package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import net.barrage.llmao.models.Role
import net.barrage.llmao.models.User
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.SessionService
import net.barrage.llmao.services.UserService

/** Key to use for obtaining users from requests validated by the session middleware. */
val RequestUser = AttributeKey<User>("User")

/**
 * Extension that quickly extracts the session ID from the cookies. Use only in contexts where you
 * are certain the session exists, e.g. after session check middleware.
 */
fun ApplicationCall.sessionId(): KUUID {
  return sessions.get<UserSession>()!!.id
}

/** Obtain the current user initiating the request. */
fun ApplicationCall.user(): User {
  return attributes[RequestUser]
}

fun Application.configureSession() {
  install(Sessions) {
    cookie<UserSession>(
      this@configureSession.environment.config.property("session.cookieName").getString()
    ) {
      cookie.path = "/"
      cookie.maxAgeInSeconds = 24 * 60 * 60 * 1 // 1 day
      cookie.httpOnly =
        this@configureSession.environment.config
          .property("session.httpOnly")
          .getString()
          .toBoolean()
      cookie.secure =
        this@configureSession.environment.config.property("session.secure").getString().toBoolean()
      cookie.domain =
        this@configureSession.environment.config.property("session.domain").getString()
      cookie.extensions["SameSite"] = "Lax"
    }
  }

  authentication {
    session<UserSession>("auth-session") {
      validate { session ->
        if (session.id.toString().isEmpty()) {
          return@validate null
        }

        val serverSession = SessionService().get(session.id)

        if (serverSession == null || !serverSession.isValid()) {
          return@validate null
        }

        val user = UserService().get(serverSession.userId)

        if (!user.active) {
          return@validate null
        }

        attributes.put(RequestUser, user)

        return@validate session
      }

      challenge { call.respond(HttpStatusCode.Unauthorized, "Unauthorized access") }
    }

    session<UserSession>("auth-session-admin") {
      validate { session ->
        if (session.id.toString().isEmpty()) {
          return@validate null
        }

        val serverSession = SessionService().get(session.id)

        if (serverSession == null || !serverSession.isValid()) {
          return@validate null
        }

        val user = UserService().get(serverSession.userId)

        if (!user.active || user.role != Role.ADMIN.name) {
          return@validate null
        }

        attributes.put(RequestUser, user)

        return@validate session
      }
      challenge { call.respond(HttpStatusCode.Unauthorized, "Unauthorized access") }
    }
  }
}

fun Application.extendSession() {
  intercept(Plugins) {
    val userSession = call.sessions.get<UserSession>() ?: return@intercept

    val sessionService = SessionService()

    val serverSession = sessionService.get(userSession.id) ?: return@intercept

    if (!serverSession.isValid()) {
      return@intercept
    }

    sessionService.extend(serverSession.sessionId)
    call.sessions.set(UserSession(userSession.id))
  }
}
