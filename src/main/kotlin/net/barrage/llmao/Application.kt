package net.barrage.llmao

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.api.ws.websocketServer
import net.barrage.llmao.app.workflow.chat.ChatWorkflowFactory
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.plugins.configureCors
import net.barrage.llmao.plugins.configureErrorHandling
import net.barrage.llmao.plugins.configureOpenApi
import net.barrage.llmao.plugins.configureRequestValidation
import net.barrage.llmao.plugins.configureRouting
import net.barrage.llmao.plugins.configureSerialization
import net.barrage.llmao.plugins.configureSession
import net.barrage.llmao.plugins.extendSession

fun main(args: Array<String>) {
  EngineMain.main(args)
}

fun Application.module() {
  val applicationStoppingJob: CompletableJob = Job()

  environment.monitor.subscribe(ApplicationStopping) { applicationStoppingJob.complete() }
  val stateChangeListener = EventListener<StateChangeEvent>()

  val state = ApplicationState(environment.config, applicationStoppingJob, stateChangeListener)
  val toolchainFactory = ToolchainFactory(state.services, state.repository.agent)

  configureSerialization()
  configureSession(state.services.auth)
  extendSession(state.services.auth)
  configureOpenApi()
  websocketServer(
    ChatWorkflowFactory(
      providerState = state.providers,
      agentService = state.services.agent,
      chatWorkflowRepository = state.repository.chatWorkflow,
      toolchainFactory = toolchainFactory,
      settingsService = state.settingsService,
      tokenUsageRepositoryW = state.repository.tokenUsageW,
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
