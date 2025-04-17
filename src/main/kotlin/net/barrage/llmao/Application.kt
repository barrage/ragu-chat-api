package net.barrage.llmao

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.api.http.configureCors
import net.barrage.llmao.app.api.http.configureErrorHandling
import net.barrage.llmao.app.api.http.configureOpenApi
import net.barrage.llmao.app.api.http.configureRequestValidation
import net.barrage.llmao.app.api.http.configureRouting
import net.barrage.llmao.app.api.http.installJwtAuth
import net.barrage.llmao.app.api.http.noAuth
import net.barrage.llmao.app.api.ws.websocketServer
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.types.KUUID

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
  val applicationStoppingJob: CompletableJob = Job()

  monitor.subscribe(ApplicationStopping) { applicationStoppingJob.complete() }
  val stateChangeListener = EventListener<StateChangeEvent>()

  val state = ApplicationState(environment.config, applicationStoppingJob, stateChangeListener)

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
      }
    )
  }

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

  configureOpenApi()
  websocketServer(listener = stateChangeListener)
  configureRouting(state)
  configureRequestValidation()
  configureErrorHandling()
  configureCors()
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

fun tryUuid(value: String): KUUID {
  return try {
    KUUID.fromString(value)
  } catch (e: IllegalArgumentException) {
    throw AppError.api(ErrorReason.InvalidParameter, "'$value' is not a valid UUID", original = e)
  }
}
