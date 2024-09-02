package net.barrage.llmao

import io.ktor.server.application.*
import net.barrage.llmao.plugins.Database
import net.barrage.llmao.plugins.configureRequestValidation
import net.barrage.llmao.plugins.configureRouting
import net.barrage.llmao.plugins.configureSerialization

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    Database.init(environment.config)
    configureSerialization()
    configureRouting()
    configureRequestValidation()
}
