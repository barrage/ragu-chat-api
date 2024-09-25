package net.barrage.llmao

import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import net.barrage.llmao.plugins.*
import net.barrage.llmao.weaviate.WeaviteLoader
import net.barrage.llmao.weaviate.collections.Documentation
import net.barrage.llmao.websocket.configureWebsockets

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  Database.init(environment.config)
  Documentation.init(environment.config)
  WeaviteLoader.init(environment.config)

  configureSerialization()
  configureSession()
  extendSession()
  configureOpenApi()
  configureWebsockets()
  configureRouting()
  configureRequestValidation()
  configureErrorHandling()
  configureCors()
}

fun env(application: Application, key: String): String {
  return application.environment.config.property(key).getString()
}
