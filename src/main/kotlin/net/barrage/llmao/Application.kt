package net.barrage.llmao

import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.api.ws.WebsocketChatFactory
import net.barrage.llmao.app.api.ws.websocketServer
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.plugins.configureCors
import net.barrage.llmao.plugins.configureErrorHandling
import net.barrage.llmao.plugins.configureOpenApi
import net.barrage.llmao.plugins.configureRequestValidation
import net.barrage.llmao.plugins.configureRouting
import net.barrage.llmao.plugins.configureSerialization
import net.barrage.llmao.plugins.configureSession
import net.barrage.llmao.plugins.extendSession

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  val applicationStoppingJob: CompletableJob = Job()

  environment.monitor.subscribe(ApplicationStopping) { applicationStoppingJob.complete() }
  val stateChangeListener = EventListener<StateChangeEvent>()

  val state = ApplicationState(environment.config, applicationStoppingJob, stateChangeListener)

  configureSerialization()
  configureSession(state.services.auth)
  extendSession(state.services.auth)
  configureOpenApi()
  websocketServer(
    WebsocketChatFactory(state.services.agent, state.services.conversation),
    stateChangeListener,
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
