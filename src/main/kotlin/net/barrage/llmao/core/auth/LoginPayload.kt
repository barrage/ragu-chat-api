package net.barrage.llmao.core.auth

import io.ktor.http.*
import net.barrage.llmao.app.auth.LoginSource
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class LoginPayload(
  val code: String,
  val grantType: String,
  val redirectUri: String,
  val provider: String,
  val source: LoginSource,
  val codeVerifier: String,
) {
  companion object {
    fun fromForm(form: Parameters): LoginPayload {
      val source = form["source"]
      val provider = form["provider"]
      val code = form["code"]
      val grantType = form["grant_type"]
      val redirectUri = form["redirect_uri"]
      val codeVerifier = form["code_verifier"]

      if (code.isNullOrBlank()) {
        throw AppError.api(ErrorReason.InvalidParameter, "Missing authorization code")
      }

      if (grantType.isNullOrBlank()) {
        throw AppError.api(ErrorReason.InvalidParameter, "Missing grant type")
      }

      if (redirectUri.isNullOrBlank()) {
        throw AppError.api(ErrorReason.InvalidParameter, "Missing redirect URI")
      }

      if (provider.isNullOrBlank()) {
        throw AppError.api(ErrorReason.InvalidParameter, "Missing auth provider")
      }

      if (source.isNullOrBlank()) {
        throw AppError.api(ErrorReason.InvalidParameter, "Missing login source")
      }

      if (codeVerifier.isNullOrBlank()) {
        throw AppError.api(ErrorReason.InvalidParameter, "Missing code verifier")
      }

      return LoginPayload(
        code,
        grantType,
        redirectUri,
        provider,
        LoginSource.tryFromString(source),
        codeVerifier,
      )
    }
  }
}
