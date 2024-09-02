package net.barrage.llmao.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        validate<NewAgentDTO>(NewAgentDTO::validate)
        validate<UpdateAgentDTO>(UpdateAgentDTO::validate)
    }
}