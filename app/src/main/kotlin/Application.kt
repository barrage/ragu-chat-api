package net.barrage.llmao.app

import ChatPlugin
import JiraKiraPlugin
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.routes.adminSettingsRoutes
import net.barrage.llmao.app.routes.administrationBlobRoutes
import net.barrage.llmao.app.routes.administrationRoutes
import net.barrage.llmao.app.routes.applicationInfoRoutes
import net.barrage.llmao.app.workflow.bonvoyage.BonvoyagePlugin
import net.barrage.llmao.app.ws.websocketServer
import net.barrage.llmao.core.*
import net.barrage.llmao.core.database.initDatabase
import net.barrage.llmao.core.http.*
import net.barrage.llmao.core.repository.SettingsRepository
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.workflow.SessionManager

// TODO: Remove in favor of annotation processing at one point
private const val JIRAKIRA_FEATURE_FLAG = "ktor.features.specialists.jirakira"
private const val BONVOYAGE_FEATURE_FLAG = "ktor.features.specialists.bonvoyage"

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
  val providers =
    ProviderState(
      llm = initializeInference(environment.config),
      vector = initializeVectorDatabases(environment.config),
      embedding = initializeEmbedders(environment.config),
      image = initializeImageStorage(environment.config),
    )

  val plugins = Plugins()
  val state = state(plugins)

  plugins.register(ChatPlugin())

  if (environment.config.string(JIRAKIRA_FEATURE_FLAG).toBoolean()) {
    plugins.register(JiraKiraPlugin())
  }

  if (environment.config.string(BONVOYAGE_FEATURE_FLAG).toBoolean()) {
    plugins.register(BonvoyagePlugin())
  }

  runBlocking { plugins.initialize(environment.config, state) }

  val sessionManager = SessionManager(plugins, state.listener)

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

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
      }
    )
  }

  install(RequestValidation) { with(plugins) { configureRequestValidation() } }

  routing {
    route("/__health") { get { call.respond(HttpStatusCode.OK) } }

    openApiRoutes()

    // Admin API routes
    authenticate("admin") {
      administrationBlobRoutes(state.providers.image)
      adminSettingsRoutes(state.settings)
      administrationRoutes()
    }

    authenticate("user") { applicationInfoRoutes() }

    with(plugins) { configureRoutes(state) }
  }

  configureErrorHandling()
  configureCors()
  configureOpenApi()

  websocketServer(sessionManager, plugins)
}

private fun Application.configureCors() {
  val origins = environment.config.property("cors.origins").getList()
  val methods = environment.config.property("cors.methods").getList().map { HttpMethod(it) }
  val headers = environment.config.property("cors.headers").getList()

  install(CORS) {
    this.allowCredentials = true
    this.allowOrigins { origins.contains(it) }
    this.methods.addAll(methods)
    this.headers.addAll(headers)
    this.allowNonSimpleContentTypes = true
  }
}

private fun Application.state(plugins: Plugins): ApplicationState {
  val db = initDatabase(environment.config)
  val settings = Settings(SettingsRepository(db))
  return ApplicationState(
    environment.config,
    plugins,
    initDatabase(environment.config),
    ProviderState(
      llm = initializeInference(environment.config),
      vector = initializeVectorDatabases(environment.config),
      embedding = initializeEmbedders(environment.config),
      image = initializeImageStorage(environment.config),
    ),
    settings,
  )
}
