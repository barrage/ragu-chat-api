package net.barrage.llmao.error

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
open class Error(val type: String, val reason: String, val description: String? = null) :
  Throwable() {
  override fun toString(): String {
    return Json.encodeToString(this)
  }
}

fun internalError(): Error {
  return Error("Internal", "Something went wrong")
}

fun apiError(reason: String, description: String? = null): Error {
  return Error("API", reason, description)
}
