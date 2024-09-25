package net.barrage.llmao.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.users.CreateUser
import net.barrage.llmao.dtos.users.UpdateUser
import net.barrage.llmao.dtos.users.UpdateUserAdmin

fun Application.configureRequestValidation() {
  install(RequestValidation) {
    // Register the validation functions for each DTO

    // Agent DTOs validations
    validate<NewAgentDTO>(NewAgentDTO::validate)
    validate<UpdateAgentDTO>(UpdateAgentDTO::validate)

    // User DTOs validations
    validate<CreateUser>(CreateUser::validate)
    validate<UpdateUser>(UpdateUser::validate)
    validate<UpdateUserAdmin>(UpdateUserAdmin::validate)

    // Chat DTOs validations
    validate<UpdateChatTitleDTO>(UpdateChatTitleDTO::validate)
  }
}
