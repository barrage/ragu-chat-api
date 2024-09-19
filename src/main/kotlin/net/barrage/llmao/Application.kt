package net.barrage.llmao

import io.ktor.server.application.*
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
    configureSecurity()
    configureSession()
    extendSession()
    configureOpenApi()
    configureWebsockets()
    configureRouting()
    configureRequestValidation()
    configureErrorHandling()
    configureCors()
}
