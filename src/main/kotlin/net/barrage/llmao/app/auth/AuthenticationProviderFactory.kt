package net.barrage.llmao.app.auth

import io.ktor.server.application.*
import net.barrage.llmao.core.AuthenticationFactory
import net.barrage.llmao.core.AuthenticationProvider
import net.barrage.llmao.core.httpClient
import net.barrage.llmao.env
import net.barrage.llmao.error.apiError

class AuthenticationProviderFactory(val application: Application) : AuthenticationFactory() {
  private val google: GoogleAuthenticationProvider

  init {
    google = initGoogleOAuth(application)
  }

  override fun getProvider(providerId: String): AuthenticationProvider {
    when (providerId) {
      "google" -> return google
      else -> throw apiError("Provider", "Unsupported auth provider '$providerId'")
    }
  }

  private fun initGoogleOAuth(application: Application): GoogleAuthenticationProvider {
    val client = httpClient()
    val tokenEp = env(application, "oauth.google.tokenEndpoint")
    val accEp = env(application, "oauth.google.accountEndpoint")
    val clientId = env(application, "oauth.google.clientId")
    val clientSecret = env(application, "oauth.google.clientSecret")
    return GoogleAuthenticationProvider(client, tokenEp, accEp, clientId, clientSecret)
  }
}
