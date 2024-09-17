package net.barrage.llmao.plugins

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import net.barrage.llmao.enums.LoginSource
import net.barrage.llmao.models.GoogleUserInfo
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.SessionService
import net.barrage.llmao.services.UserService

fun Application.configureSecurity() {
    authentication {
        oauth("auth-oauth-google") {
            urlProvider = {
                application.environment.config.property("oauth.google.urlProvider").getString()
            }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = application.environment.config.property("oauth.google.clientId").getString(),
                    clientSecret = application.environment.config.property("oauth.google.clientSecret").getString(),
                    defaultScopes = listOf(
                        "https://www.googleapis.com/auth/userinfo.profile",
                        "https://www.googleapis.com/auth/userinfo.email"
                    )
                )
            }
            client = HttpClient(Apache)
        }
    }


    routing {
        get("/auth/login-admin") {
            val source = LoginSource.fromString(call.request.queryParameters["source"] ?: "web")
            val sessionId = KUUID.randomUUID()
            val app = "backoffice"
            call.sessions.set(UserSession(sessionId, source.name, app))
            call.respondRedirect("/auth/login")
        }

        get("/auth/login-user") {
            val source = LoginSource.fromString(call.request.queryParameters["source"] ?: "web")
            val sessionId = KUUID.randomUUID()
            val app = "client"
            call.sessions.set(UserSession(sessionId, source.name, app))
            call.respondRedirect("/auth/login")
        }

        authenticate("auth-oauth-google") {
            get("/auth/login") {
                call.respondRedirect("/auth/callback")
            }

            get("/auth/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                if (principal != null) {
                    val userInfo = fetchUserInfo(principal.accessToken)

                    // If user is not registered this will throw user not found exception
                    val user = UserService().getByEmail(userInfo.email)

                    val session = call.sessions.get<UserSession>()
                    val source = session?.source ?: "web"
                    val app = session?.app ?: "client"
                    call.sessions.clear<UserSession>()

                    val sessionId = KUUID.randomUUID()
                    call.sessions.set(UserSession(sessionId))
                    SessionService().store(sessionId, user.id)

                    // Redirect to different pages based on user role
                    if (app == "backoffice") {
                        if (user.role == "ADMIN") {
                            when (source) {
                                // TODO: frontend urls for backoffice
                                LoginSource.IOS.name -> call.respondRedirect("/hello-admin-ios")
                                LoginSource.ANDROID.name -> call.respondRedirect("/hello-admin-android")
                                else -> call.respondRedirect("/hello-admin")
                            }
                            return@get
                        } else if (user.role == "USER") {
                            call.response.status(HttpStatusCode.Unauthorized)
                            // TODO: frontend /login url
                            call.respondRedirect("/hello-login")
                            return@get
                        }
                    } else {
                        when (source) {
                            // TODO: frontend urls for client
                            LoginSource.IOS.name -> call.respondRedirect("/hello-user-ios")
                            LoginSource.ANDROID.name -> call.respondRedirect("/hello-user-android")
                            else -> call.respondRedirect("/hello-user")
                        }
                        return@get
                    }
                }
                // TODO: frontend /login url
                call.respondRedirect("/hello-login")
                return@get
            }
        }

        post("/auth/logout") {
            val userSession = call.sessions.get<UserSession>()
            val serverSession = SessionService().get(userSession!!.id)
            if (serverSession != null && serverSession.isValid()) {
                SessionService().expire(serverSession.sessionId)
            }
            call.sessions.clear<UserSession>()
            // TODO: frontend /login url
            call.respondRedirect("/hello-login")
            return@post
        }

    }
}

private val json = Json { ignoreUnknownKeys = true }

suspend fun fetchUserInfo(accessToken: String): GoogleUserInfo {
    val client = HttpClient(Apache)
    val response: HttpResponse = client.get("https://www.googleapis.com/oauth2/v2/userinfo") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
        }
    }
    val responseBody = response.bodyAsText()
    return json.decodeFromString<GoogleUserInfo>(responseBody)
}