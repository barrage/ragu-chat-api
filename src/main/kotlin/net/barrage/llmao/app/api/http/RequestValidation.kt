package net.barrage.llmao.app.api.http

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import net.barrage.llmao.app.adapters.whatsapp.model.UpdateNumber
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.core.model.CreateAgent
import net.barrage.llmao.core.model.UpdateAgent
import net.barrage.llmao.core.model.UpdateCollections

fun Application.configureRequestValidation() {
  install(RequestValidation) {
    // Register the validation functions for each DTO

    // Agent DTOs validations
    validate<CreateAgent>(CreateAgent::validate)
    validate<UpdateAgent>(UpdateAgent::validate)
    validate<UpdateCollections>(UpdateCollections::validate)

    // Chat DTOs validations
    validate<UpdateChatTitleDTO>(UpdateChatTitleDTO::validate)

    // WhatsApp DTOs validations
    validate<UpdateNumber>(UpdateNumber::validate)
  }
}
