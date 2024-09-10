package net.barrage.llmao.dtos.users

import net.barrage.llmao.enums.Roles
import net.barrage.llmao.repositories.AgentRepository

fun validateUsername(username: String): String? {
    return when {
        username.isBlank() -> "username must not be empty"
        username.length < 3 -> "username must be at least 3 characters long"
        else -> null
    }
}

fun validateEmail(email: String): String? {
    return when {
        email.isBlank() -> "email must not be empty"
        !email.matches(Regex("^[\\w\\-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) -> "email must be a valid email address"
        else -> null
    }
}

fun validatePassword(password: String): String? {
    return when {
        password.isBlank() -> "password must not be empty"
        password.length < 3 -> "password must be at least 3 characters long"
        else -> null
    }
}

fun validateFirstName(firstName: String): String? {
    return when {
        firstName.isBlank() -> "firstName must not be empty"
        else -> null
    }
}

fun validateLastName(lastName: String): String? {
    return when {
        lastName.isBlank() -> "lastName must not be empty"
        else -> null
    }
}

fun validateDefaultAgentId(defaultAgentId: Int): String? {
    return when {
        AgentRepository().get(defaultAgentId) == null -> "defaultAgentId must be a valid agent ID"
        else -> null
    }
}

fun validateNewPasswordsMatch(newPassword: String, newPasswordCopy: String): String? {
    return when {
        newPassword != newPasswordCopy -> "new passwords must match"
        else -> null
    }
}

fun validateNewPasswordDifferentFromPrevious(newPassword: String, previousPassword: String): String? {
    return when {
        newPassword == previousPassword -> "new password must be different from the previous password"
        else -> null
    }
}
