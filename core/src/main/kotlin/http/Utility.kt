package net.barrage.llmao.core.http

import io.ktor.server.application.ApplicationCall
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.types.KUUID

/**
 * Utility for quickly obtaining a path segment from a URL and converting it to a UUID. Throws an
 * [AppError] if the UUID is malformed.
 */
fun ApplicationCall.pathUuid(param: String): KUUID {
    val value = parameters[param]
    try {
        return KUUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        throw AppError.api(
            ErrorReason.InvalidParameter,
            "'$value' is not a valid UUID",
            original = e
        )
    }
}
