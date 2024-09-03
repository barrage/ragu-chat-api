package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*

class UpdateUserDTO(
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val defaultAgentId: Int,
) {
    fun validate(): ValidationResult {
        val rules = listOf(
            validateUsername(username),
            validateEmail(email),
            validateFirstName(firstName),
            validateLastName(lastName),
            validateDefaultAgentId(defaultAgentId)
        )

        val errors: List<String> = rules.filterNotNull()

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}