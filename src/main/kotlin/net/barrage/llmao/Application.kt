package net.barrage.llmao

import impl.administration.administrationBlobRoutes
import impl.administration.administrationRoutes
import impl.administration.applicationInfoRoutes
import impl.administration.settings.adminSettingsRoutes
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.cio.EngineMain
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.workflow.bonvoyage.BonvoyagePlugin
import net.barrage.llmao.app.workflow.chat.ChatPlugin
import net.barrage.llmao.app.workflow.hgk.HgkPlugin
import net.barrage.llmao.app.workflow.jirakira.JiraKiraPlugin
import net.barrage.llmao.app.ws.websocketServer
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.Plugins
import net.barrage.llmao.core.http.configureErrorHandling
import net.barrage.llmao.core.http.configureOpenApi
import net.barrage.llmao.core.http.installJwtAuth
import net.barrage.llmao.core.http.noAuth
import net.barrage.llmao.core.http.openApiRoutes
import net.barrage.llmao.core.state
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.SessionManager

// TODO: Remove in favor of annotation processing at one point
private const val JIRAKIRA_FEATURE_FLAG = "ktor.features.specialists.jirakira"
private const val BONVOYAGE_FEATURE_FLAG = "ktor.features.specialists.bonvoyage"

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
  val plugins = Plugins()
  val state = state(plugins)
  
  plugins.register(ChatPlugin())
  plugins.register(HgkPlugin())

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

/** Shorthand for `config.property(key).getString()` */
fun ApplicationConfig.string(key: String): String {
    return property(key).getString()
}

/** Shorthand for `config.property(key).getString().toLong` */
fun ApplicationConfig.long(key: String): Long {
    return property(key).getString().toLong()
}

/** Shorthand for `config.property(key).getString().toInt` */
fun ApplicationConfig.int(key: String): Int {
    return property(key).getString().toInt()
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

fun tryUuid(value: String): KUUID {
    return try {
        KUUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        throw AppError.api(
            ErrorReason.InvalidParameter,
            "'$value' is not a valid UUID",
            original = e
        )
    }
}
