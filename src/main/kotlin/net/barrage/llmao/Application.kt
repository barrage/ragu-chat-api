package net.barrage.llmao

import io.ktor.server.application.*
import net.barrage.llmao.plugins.*
import net.barrage.llmao.weaviate.loadWeaviate
import net.barrage.llmao.websocket.configureWebsockets

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    Database.init(environment.config)
    val weavite = loadWeaviate(environment.config)
    configureSerialization()
    configureSecurity()
    configureSession()
    extendSession()
    configureOpenApi()
    configureWebsockets(weavite)
    configureRouting()
    configureRequestValidation()
    configureErrorHandling()
    configureCors()
}
