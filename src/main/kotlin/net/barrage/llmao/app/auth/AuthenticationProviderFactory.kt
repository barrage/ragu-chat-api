package net.barrage.llmao.app.auth

import io.ktor.server.application.*
import net.barrage.llmao.core.AuthenticationFactory
import net.barrage.llmao.core.AuthenticationProvider
import net.barrage.llmao.core.httpClient
import net.barrage.llmao.env
import net.barrage.llmao.error.apiError

class AuthenticationProviderFactory(env: ApplicationEnvironment) : AuthenticationFactory() {
  private val google: GoogleAuthenticationProvider

  init {
    google = initGoogleOAuth(env)
  }

  override fun getProvider(providerId: String): AuthenticationProvider {
    return when (providerId) {
      google.id() -> google
      else -> throw apiError("Provider", "Unsupported auth provider '$providerId'")
    }
  }

  private fun initGoogleOAuth(env: ApplicationEnvironment): GoogleAuthenticationProvider {
    val client = httpClient()
    val tokenEp = env(env, "oauth.google.tokenEndpoint")
    val accEp = env(env, "oauth.google.accountEndpoint")
    val clientId = env(env, "oauth.google.clientId")
    val clientSecret = env(env, "oauth.google.clientSecret")
    return GoogleAuthenticationProvider(client, tokenEp, accEp, clientId, clientSecret)
  }
}
