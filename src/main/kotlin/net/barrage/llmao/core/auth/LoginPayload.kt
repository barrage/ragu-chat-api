package net.barrage.llmao.core.auth

import io.ktor.http.*
import net.barrage.llmao.app.auth.LoginSource
import net.barrage.llmao.error.apiError

class LoginPayload(
  val code: String,
  val grantType: String,
  val redirectUri: String,
  val provider: String,
  val source: LoginSource,
) {
  companion object {
    fun fromForm(form: Parameters): LoginPayload {
      val source = form["source"]
      val provider = form["provider"]
      val code = form["code"]
      val grantType = form["grant_type"]
      val redirectUri = form["redirect_uri"]

      if (code.isNullOrBlank()) {
        throw apiError("Validation", "Missing authorization code")
      }

      if (grantType.isNullOrBlank()) {
        throw apiError("Validation", "Missing grant type")
      }

      if (redirectUri.isNullOrBlank()) {
        throw apiError("Validation", "Missing redirect URI")
      }

      if (provider.isNullOrBlank()) {
        throw apiError("Validation", "Missing auth provider")
      }

      if (source.isNullOrBlank()) {
        throw apiError("Validation", "Missing login source")
      }

      return LoginPayload(code, grantType, redirectUri, provider, LoginSource.tryFromString(source))
    }
  }
}
