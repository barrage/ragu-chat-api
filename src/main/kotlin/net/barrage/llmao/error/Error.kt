package net.barrage.llmao.error

import kotlinx.serialization.Serializable

@Serializable
open class Error(
    val type: String,
    val cause: String,
    val description: String? = null
)

fun internalError(): Error {
    return Error("Internal", "Internal server error", "Something went wrong")
}

fun apiError(cause: String, description: String? = null): Error {
    return Error("API", cause, description)
}