package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable

@Serializable
class NewUserDTO(
    val username: String,
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val defaultAgentId: Int,
) {
    fun validate(): ValidationResult {
        val rules = listOf(
            validateUsername(username),
            validateEmail(email),
            validatePassword(password),
            validateFirstName(firstName),
            validateLastName(lastName),
            validateRole(role),
            validateDefaultAgentId(defaultAgentId)
        )

        val errors: List<String> = rules.filterNotNull()

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}