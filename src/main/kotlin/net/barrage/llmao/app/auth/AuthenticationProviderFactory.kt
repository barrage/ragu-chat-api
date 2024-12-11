package net.barrage.llmao.app.auth

import io.ktor.server.config.*
import net.barrage.llmao.app.auth.apple.AppleAuthenticationProvider
import net.barrage.llmao.app.auth.carnet.CarnetAuthenticationProvider
import net.barrage.llmao.app.auth.google.GoogleAuthenticationProvider
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.auth.AuthenticationProvider
import net.barrage.llmao.core.httpClient
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

class AuthenticationProviderFactory(val config: ApplicationConfig) :
  ProviderFactory<AuthenticationProvider>() {
  private var providers = mutableMapOf<String, AuthenticationProvider>()

  init {
    config.tryGetString("ktor.features.oauth.google")?.toBoolean()?.let { enabled ->
      if (enabled) {
        providers["google"] = initGoogleOAuth()
      }
    }
    config.tryGetString("ktor.features.oauth.apple")?.toBoolean()?.let { enabled ->
      if (enabled) {
        providers["apple"] = initAppleOAuth()
      }
    }
    config.tryGetString("ktor.features.oauth.carnet")?.toBoolean()?.let { enabled ->
      if (enabled) {
        providers["carnet"] = initCarnetOAuth()
      }
    }
  }

  override fun getProvider(providerId: String): AuthenticationProvider {
    return providers[providerId]
      ?: throw AppError.api(ErrorReason.InvalidProvider, "Unsupported auth provider '$providerId'")
  }

  override fun listProviders(): List<String> {
    return providers.keys.toList()
  }

  private fun initGoogleOAuth(): GoogleAuthenticationProvider {
    val client = httpClient()
    val tokenEp = config.string("oauth.google.tokenEndpoint")
    val keysEp = config.string("oauth.google.keysEndpoint")
    val tokenIssuer = config.string("oauth.google.tokenIssuer")
    val clientId = config.string("oauth.google.clientId")
    val clientSecret = config.string("oauth.google.clientSecret")
    return GoogleAuthenticationProvider(
      client,
      tokenEp,
      keysEp,
      tokenIssuer,
      clientId,
      clientSecret,
    )
  }

  private fun initAppleOAuth(): AppleAuthenticationProvider {
    val client = httpClient()
    val tokenIssuer = config.string("oauth.apple.tokenIssuer")
    val tokenEp = config.string("oauth.apple.tokenEndpoint")
    val keysEp = config.string("oauth.apple.keysEndpoint")
    val clientId = config.string("oauth.apple.clientId")
    val serviceId = config.string("oauth.apple.serviceId")
    val teamId = config.string("oauth.apple.teamId")
    val keyId = config.string("oauth.apple.keyId")
    val clientSecret = config.string("oauth.apple.clientSecret")
    return AppleAuthenticationProvider(
      client,
      tokenEp,
      keysEp,
      tokenIssuer,
      clientId,
      serviceId,
      teamId,
      keyId,
      clientSecret,
    )
  }

  private fun initCarnetOAuth(): CarnetAuthenticationProvider {
    val client = httpClient()
    val tokenIssuer = config.string("oauth.carnet.tokenIssuer")
    val clientId = config.string("oauth.carnet.clientId")
    val clientSecret = config.string("oauth.carnet.clientSecret")
    val tokenEp = config.string("oauth.carnet.tokenEndpoint")
    val userInfoEp = config.string("oauth.carnet.userInfoEndpoint")
    val keysEp = config.string("oauth.carnet.keysEndpoint")
    return CarnetAuthenticationProvider(
      client,
      tokenEp,
      keysEp,
      userInfoEp,
      tokenIssuer,
      clientId,
      clientSecret,
    )
  }
}
