package net.barrage.llmao.core.model

/**
 * When obtained from access tokens, the username and email will be present. When loaded for certain
 * adapters (e.g. WhatsApp) the username and email are not guaranteed to be present.
 */
data class User(
  val id: String,
  val username: String,
  val email: String,
  val entitlements: List<String>,
) {
  fun isAdmin(): Boolean {
    return entitlements.contains("admin")
  }
}
