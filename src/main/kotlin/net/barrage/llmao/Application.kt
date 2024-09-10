package net.barrage.llmao

import io.ktor.server.application.*
import net.barrage.llmao.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    Database.init(environment.config)
    configureSerialization()
    configureOpenApi()
    configureRouting()
    configureRequestValidation()
    configureErrorHandling()
    configureCors()
}
