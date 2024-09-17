package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable

@Serializable
class UpdateUserDTO(
    override val firstName: String,
    override val lastName: String,
    override val defaultAgentId: Int,
) : UpdateUser {
    fun validate(): ValidationResult {
        val rules = listOf(
            validateFirstName(firstName),
            validateLastName(lastName),
            validateDefaultAgentId(defaultAgentId)
        )

        val errors: List<String> = rules.filterNotNull()

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}