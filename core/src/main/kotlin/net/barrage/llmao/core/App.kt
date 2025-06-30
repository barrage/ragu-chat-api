package net.barrage.llmao.core

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.http.configureCors
import net.barrage.llmao.core.http.configureErrorHandling
import net.barrage.llmao.core.http.configureOpenApi
import net.barrage.llmao.core.http.installJwtAuth
import net.barrage.llmao.core.http.noAuth
import net.barrage.llmao.core.http.openApiRoutes
import net.barrage.llmao.core.routes.adminSettingsRoutes
import net.barrage.llmao.core.routes.administrationBlobRoutes
import net.barrage.llmao.core.routes.administrationRoutes
import net.barrage.llmao.core.routes.applicationInfoRoutes
import net.barrage.llmao.core.workflow.SessionManager
import net.barrage.llmao.core.ws.websocketServer

fun Application.configureCore(state: ApplicationState) {
  runBlocking { Plugins.initialize(environment.config, state) }

  initializeCore()

  routing {
    route("/__health") { get { call.respond(HttpStatusCode.OK) } }
    openApiRoutes()
    authenticate("user") { applicationInfoRoutes() }
    // Admin API routes
    authenticate("admin") {
      administrationBlobRoutes(state.providers.image)
      adminSettingsRoutes(state.settings)
      administrationRoutes()
    }
    with(Plugins) { configureRoutes(state) }
  }
}

fun Application.initializeCore() {
  if (environment.config.string("jwt.enabled").toBoolean()) {
    installJwtAuth(
      issuer = environment.config.string("jwt.issuer"),
      jwksEndpoint = environment.config.string("jwt.jwksEndpoint"),
      leeway = environment.config.long("jwt.leeway"),
      entitlementsClaim = environment.config.string("jwt.entitlementsClaim"),
      audience = environment.config.string("jwt.audience"),
    )
  } else {
    noAuth()
  }

  configureErrorHandling()
  configureCors()
  configureOpenApi()

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
      }
    )
  }

  install(RequestValidation) { with(Plugins) { configureRequestValidation() } }

  websocketServer(SessionManager())
}
