package net.barrage.llmao.app.auth

import io.ktor.server.config.*
import net.barrage.llmao.app.auth.apple.AppleAuthenticationProvider
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
  private val apple: AppleAuthenticationProvider

  init {
    google = initGoogleOAuth(config)
    apple = initAppleOAuth(config)
  }

  override fun getProvider(providerId: String): AuthenticationProvider {
    return when (providerId) {
      google.id() -> google
      apple.id() -> apple
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

  private fun initAppleOAuth(config: ApplicationConfig): AppleAuthenticationProvider {
    val client = httpClient()
    val authEp = config.string("oauth.apple.endpoint")
    val clientId = config.string("oauth.apple.clientId")
    val serviceId = config.string("oauth.apple.serviceId")
    val teamId = config.string("oauth.apple.teamId")
    val keyId = config.string("oauth.apple.keyId")
    val privateKey = config.string("oauth.apple.clientSecret")
    return AppleAuthenticationProvider(
      client,
      authEp,
      clientId,
      serviceId,
      teamId,
      keyId,
      privateKey,
    )
  }
}
