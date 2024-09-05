package net.barrage.llmao.error

import kotlinx.serialization.Serializable

@Serializable
open class Error(
    private val type: String,
    private val cause: String,
    private val description: String? = null
)

fun internalError(): Error {
    return Error("Internal", "Internal server error", "Something went wrong")
}

fun apiError(cause: String, description: String? = null): Error {
    return Error("API", cause, description)
}