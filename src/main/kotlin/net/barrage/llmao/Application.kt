package net.barrage.llmao

import com.knuddels.jtokkit.Encodings
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.api.http.authMiddleware
import net.barrage.llmao.app.api.http.configureCors
import net.barrage.llmao.app.api.http.configureOpenApi
import net.barrage.llmao.app.api.http.configureRequestValidation
import net.barrage.llmao.app.api.http.configureRouting
import net.barrage.llmao.app.api.ws.websocketServer
import net.barrage.llmao.app.workflow.chat.ChatWorkflowFactory
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.error.configureErrorHandling

fun main(args: Array<String>) {
  EngineMain.main(args)
}

fun Application.module() {
  val applicationStoppingJob: CompletableJob = Job()

  environment.monitor.subscribe(ApplicationStopping) { applicationStoppingJob.complete() }
  val stateChangeListener = EventListener<StateChangeEvent>()

  val state = ApplicationState(environment.config, applicationStoppingJob, stateChangeListener)
  val toolchainFactory = ToolchainFactory(state.services, state.repository.agent)

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
      }
    )
  }
  authMiddleware(
    environment.config.string("jwt.issuer"),
    environment.config.string("jwt.jwksEndpoint"),
  )
  configureOpenApi()
  websocketServer(
    ChatWorkflowFactory(
      providerState = state.providers,
      agentService = state.services.agent,
      chatWorkflowRepository = state.repository.chatWorkflow,
      toolchainFactory = toolchainFactory,
      settingsService = state.settingsService,
      tokenUsageRepositoryW = state.repository.tokenUsageW,
      encodingRegistry = Encodings.newDefaultEncodingRegistry(),
    ),
    listener = stateChangeListener,
    adapters = state.adapters,
  )
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

fun tryUuid(value: String): KUUID {
  return try {
    KUUID.fromString(value)
  } catch (e: IllegalArgumentException) {
    throw AppError.api(ErrorReason.InvalidParameter, "'$value' is not a valid UUID")
  }
}
