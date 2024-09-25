package net.barrage.llmao.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.users.AdminUpdateUserDTO
import net.barrage.llmao.dtos.users.NewDevUserDTO
import net.barrage.llmao.dtos.users.NewUserDTO
import net.barrage.llmao.dtos.users.UpdateUserDTO

fun Application.configureRequestValidation() {
  install(RequestValidation) {
    // Register the validation functions for each DTO

    // Agent DTOs validations
    validate<NewAgentDTO>(NewAgentDTO::validate)
    validate<UpdateAgentDTO>(UpdateAgentDTO::validate)

    // User DTOs validations
    validate<NewUserDTO>(NewUserDTO::validate)
    validate<UpdateUserDTO>(UpdateUserDTO::validate)
    validate<NewDevUserDTO>(NewDevUserDTO::validate)
    validate<AdminUpdateUserDTO>(AdminUpdateUserDTO::validate)

    // Chat DTOs validations
    validate<UpdateChatTitleDTO>(UpdateChatTitleDTO::validate)
  }
}
