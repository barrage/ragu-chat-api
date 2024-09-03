package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*

class UpdateUserRoleDTO(
    val role: String
) {
    fun validate(): ValidationResult {
        val rules = listOf(
            validateRole(role)
        )

        val errors: List<String> = rules.filterNotNull()

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}