package net.barrage.llmao

import io.ktor.server.application.*
import io.ktor.server.config.*
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.ServiceState
import net.barrage.llmao.app.api.ws.ChatFactory
import net.barrage.llmao.app.api.ws.Server
import net.barrage.llmao.app.api.ws.websocketServer
import net.barrage.llmao.plugins.*

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  val state = ApplicationState(environment.config)
  val services = ServiceState(state)

  val chatFactory = ChatFactory(services.agent, services.chat)
  val websocketServer = Server(chatFactory)

  configureSerialization()
  configureSession(services.auth)
  extendSession(services.auth)
  configureOpenApi()
  websocketServer(websocketServer)
  configureRouting(services)
  configureRequestValidation()
  configureErrorHandling()
  configureCors()
}

fun configString(config: ApplicationConfig, key: String): String {
  return config.property(key).getString()
}
