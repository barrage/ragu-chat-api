package net.barrage.llmao

import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import net.barrage.llmao.app.LlmProviderFactory
import net.barrage.llmao.app.auth.AuthenticationProviderFactory
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.llm.factories.ChatFactory
import net.barrage.llmao.plugins.*
import net.barrage.llmao.repositories.ChatRepository
import net.barrage.llmao.repositories.SessionRepository
import net.barrage.llmao.repositories.UserRepository
import net.barrage.llmao.services.AgentService
import net.barrage.llmao.weaviate.WeaviteLoader
import net.barrage.llmao.websocket.Server
import net.barrage.llmao.websocket.websocketServer

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  Database.init(environment.config)
  WeaviteLoader.init(environment.config)

  val llmProviderFactory = LlmProviderFactory(environment)
  val authProviderFactory = AuthenticationProviderFactory(environment)

  val authService =
    AuthenticationService(authProviderFactory, SessionRepository(), UserRepository())

  val chatService = ChatService(ChatRepository(), WeaviteLoader.weaver)
  val chatFactory = ChatFactory(llmProviderFactory, AgentService(), chatService)

  val websocketServer = Server(chatFactory)

  configureSerialization()
  configureSession()
  extendSession()
  configureOpenApi()
  websocketServer(websocketServer)
  // TODO: Service state
  configureRouting(authService, chatService)
  configureRequestValidation()
  configureErrorHandling()
  configureCors()
}

fun env(env: ApplicationEnvironment, key: String): String {
  return env.config.property(key).getString()
}
