package net.barrage.llmao

import io.ktor.server.application.*
import io.ktor.server.config.*
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.ServiceState
import net.barrage.llmao.app.api.ws.ChatFactory
import net.barrage.llmao.app.api.ws.websocketServer
import net.barrage.llmao.plugins.configureApiRoutes
import net.barrage.llmao.plugins.configureCors
import net.barrage.llmao.plugins.configureErrorHandling
import net.barrage.llmao.plugins.configureOpenApi
import net.barrage.llmao.plugins.configureRequestValidation
import net.barrage.llmao.plugins.configureSerialization
import net.barrage.llmao.plugins.configureSession
import net.barrage.llmao.plugins.extendSession

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  val state = ApplicationState(environment.config)
  val services = ServiceState(state)

  configureSerialization()
  configureSession(services.auth)
  extendSession(services.auth)
  configureOpenApi()
  websocketServer(ChatFactory(services.agent, services.chat))
  configureRouting(services)
  configureRequestValidation()
  configureErrorHandling()
  configureCors()
}

/** Shorthand for `config.property(key).getString()` */
fun ApplicationConfig.string(key: String): String {
  return property(key).getString()
}
