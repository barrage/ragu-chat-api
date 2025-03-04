package net.barrage.llmao.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.UpdateUserAdmin

fun Application.configureRequestValidation() {
  install(RequestValidation) {
    // Register the validation functions for each DTO

    // Agent DTOs validations
    validate<CreateAgent>(CreateAgent::validate)
    validate<UpdateAgent>(UpdateAgent::validate)
    validate<UpdateCollections>(UpdateCollections::validate)

    // User DTOs validations
    validate<CreateUser>(CreateUser::validate)
    validate<UpdateUser>(UpdateUser::validate)
    validate<UpdateUserAdmin>(UpdateUserAdmin::validate)

    // Chat DTOs validations
    validate<UpdateChatTitleDTO>(UpdateChatTitleDTO::validate)

    // WhatsApp DTOs validations
    validate<PhoneNumber>(PhoneNumber::validate)
  }
}
