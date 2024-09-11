package net.barrage.llmao.error

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
open class Error(
    val type: String,
    val cause: String,
    val description: String? = null
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

fun internalError(): Error {
    return Error("Internal", "Internal server error", "Something went wrong")
}

fun apiError(cause: String, description: String? = null): Error {
    return Error("API", cause, description)
}