package net.barrage.llmao.dtos.users

import io.ktor.server.plugins.requestvalidation.*

class UpdateUserPasswordDTO(
    val password: String,
    val newPassword: String,
    private val newPasswordConfirmation: String
) {
    fun validate(): ValidationResult {
        val rules = listOf(
            validatePassword(password),
            validatePassword(newPassword),
            validatePassword(newPasswordConfirmation),
            validateNewPasswordDifferentFromPrevious(password, newPassword),
            validateNewPasswordsMatch(
                newPassword, newPasswordConfirmation
            )
        )

        val errors: List<String> = rules.filterNotNull()

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}