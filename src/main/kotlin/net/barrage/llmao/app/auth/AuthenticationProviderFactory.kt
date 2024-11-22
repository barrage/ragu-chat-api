package net.barrage.llmao.app.auth

import io.ktor.server.config.*
import net.barrage.llmao.app.auth.google.GoogleAuthenticationProvider
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.auth.AuthenticationProvider
import net.barrage.llmao.core.httpClient
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

class AuthenticationProviderFactory(config: ApplicationConfig) :
  ProviderFactory<AuthenticationProvider>() {
  private val google: GoogleAuthenticationProvider

  init {
    google = initGoogleOAuth(config)
  }

  override fun getProvider(providerId: String): AuthenticationProvider {
    return when (providerId) {
      google.id() -> google
      else ->
        throw AppError.api(ErrorReason.InvalidProvider, "Unsupported auth provider '$providerId'")
    }
  }

  override fun listProviders(): List<String> {
    return listOf(google.id())
  }

  private fun initGoogleOAuth(config: ApplicationConfig): GoogleAuthenticationProvider {
    val client = httpClient()
    val tokenEp = config.string("oauth.google.tokenEndpoint")
    val accEp = config.string("oauth.google.accountEndpoint")
    val clientId = config.string("oauth.google.clientId")
    val clientSecret = config.string("oauth.google.clientSecret")
    return GoogleAuthenticationProvider(client, tokenEp, accEp, clientId, clientSecret)
  }
}
