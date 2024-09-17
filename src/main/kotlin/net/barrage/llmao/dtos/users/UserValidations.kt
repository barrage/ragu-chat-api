package net.barrage.llmao.dtos.users

import net.barrage.llmao.repositories.AgentRepository

fun validateEmail(email: String): String? {
    return when {
        email.isBlank() -> "email must not be empty"
        !email.matches(Regex("^[\\w\\-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) -> "email must be a valid email address"
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
