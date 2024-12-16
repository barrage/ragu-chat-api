package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import net.barrage.llmao.app.api.http.dto.SessionCookie
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.types.KUUID

/** Key to use for obtaining users from requests validated by the session middleware. */
val RequestUser = AttributeKey<User>("User")

/**
 * Extension that quickly extracts the session ID from the cookies. Use only in contexts where you
 * are certain the session exists, e.g. after session check middleware.
 */
fun ApplicationCall.sessionId(): KUUID {
  return sessions.get<SessionCookie>()!!.id
}

/**
 * Obtain the current user initiating the request. The user is put in the request attributes from
 * the database via the session middleware.
 */
fun ApplicationCall.user(): User {
  return attributes[RequestUser]
}

fun Application.configureSession(service: AuthenticationService) {
  install(Sessions) {
    cookie<SessionCookie>(
      this@configureSession.environment.config.property("cookies.session.cookieName").getString()
    ) {
      cookie.path = "/"
      cookie.maxAgeInSeconds =
        this@configureSession.environment.config
          .property("cookies.session.maxAge")
          .getString()
          .toLong() // 1 day
      cookie.httpOnly =
        this@configureSession.environment.config
          .property("cookies.session.httpOnly")
          .getString()
          .toBoolean()
      cookie.secure =
        this@configureSession.environment.config
          .property("cookies.session.secure")
          .getString()
          .toBoolean()
      cookie.domain =
        this@configureSession.environment.config.property("cookies.session.domain").getString()
      cookie.extensions["SameSite"] =
        this@configureSession.environment.config.property("cookies.session.sameSite").getString()
    }
  }

  authentication {
    session<SessionCookie>("auth-session") {
      validate { session ->
        val (_, user) = service.validateUserSession(session.id) ?: return@validate null
        attributes.put(RequestUser, user)
        return@validate session
      }

      challenge { call.respond(HttpStatusCode.Unauthorized, "Unauthorized access") }
    }

    session<SessionCookie>("auth-session-admin") {
      validate { session ->
        val (_, user) = service.validateAdminSession(session.id) ?: return@validate null
        attributes.put(RequestUser, user)
        return@validate session
      }
      challenge { call.respond(HttpStatusCode.Unauthorized, "Unauthorized access") }
    }
  }
}

fun Application.extendSession(service: AuthenticationService) {
  intercept(Plugins) {
    val userSession = call.sessions.get<SessionCookie>() ?: return@intercept
    service.extend(userSession.id)
    call.sessions.set(SessionCookie(userSession.id))
  }
}
