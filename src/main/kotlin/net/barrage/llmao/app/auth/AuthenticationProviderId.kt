package net.barrage.llmao.app.auth

import net.barrage.llmao.error.apiError

/** Enum representing all supported authentication/authorization providers. */
enum class AuthenticationProviderId {
  Google;

  companion object {
    fun tryFromString(value: String): AuthenticationProviderId {
      when (value) {
        "google" -> return Google
        else -> throw apiError("Provider", "Unsupported auth provider '$value'")
      }
    }
  }
}
