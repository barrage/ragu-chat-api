package net.barrage.llmao.app.auth.google

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.*
import net.barrage.llmao.app.auth.JsonWebKeys
import net.barrage.llmao.core.auth.AuthenticationProvider
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.auth.UserInfo
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.auth.google")

class GoogleAuthenticationProvider(
  private val client: HttpClient,
  private val tokenEndpoint: String,
  private val keysEndpoint: String,
  private val clientId: String,
  private val clientSecret: String,
) : AuthenticationProvider {
  override fun id(): String {
    return "google"
  }

  override suspend fun authenticate(payload: LoginPayload): UserInfo {
    val params =
      Parameters.build {
        append("code", payload.code)
        append("redirect_uri", payload.redirectUri)
        append("grant_type", payload.grantType)
        append("client_id", clientId)
        append("client_secret", clientSecret)
        append("code_verifier", payload.codeVerifier)
      }

    val res = client.submitForm(url = tokenEndpoint, params, encodeInQuery = false)

    if (res.status != HttpStatusCode.OK) {
      val errorBody = res.bodyAsText()
      LOG.error("Failed to retrieve token: ${res.status}. Response: $errorBody")
      throw AppError.api(
        ErrorReason.Authentication,
        "Failed to retrieve token: ${res.status}. Response: $errorBody",
      )
    }

    val response = res.body<GoogleTokenResponse>()
    val idToken = JWT.decode(response.idToken)

    verifyJwtSignature(idToken)

    val googleId = idToken.subject

    if (idToken.expiresAtAsInstant.isBefore(Instant.now())) {
      throw AppError.api(ErrorReason.Authentication, "Invalid token signature; Token expired")
    }

    val email =
      idToken.getClaim("email")?.asString()
        ?: throw AppError.api(ErrorReason.Authentication, "Email not found")

    val name = idToken.getClaim("name")?.asString()

    val givenName = idToken.getClaim("given_name")?.asString()

    val familyName = idToken.getClaim("family_name")?.asString()

    val isVerified = idToken.getClaim("email_verified")?.asBoolean() ?: false

    if (!isVerified) {
      throw AppError.api(ErrorReason.Authentication, "Email not verified")
    }

    return UserInfo(googleId, email, name, givenName, familyName)
  }

  private suspend fun verifyJwtSignature(jwt: DecodedJWT) {
    val key = getPublicKey(jwt.keyId)

    val algorithm = Algorithm.RSA256(key, null)

    val verifier =
      JWT.require(algorithm)
        .withIssuer("https://accounts.google.com")
        .withAudience(clientId)
        .build()

    try {
      verifier.verify(jwt)
    } catch (e: Exception) {
      throw AppError.api(ErrorReason.Authentication, "Invalid token")
    }
  }

  private suspend fun getPublicKey(kid: String): RSAPublicKey {
    val res = client.get(keysEndpoint)
    if (res.status != HttpStatusCode.OK) {
      LOG.error("Unable to validate token signature; Unable to retrieve public keys")
      throw AppError.internal("Unable to validate token signature; Unable to retrieve public keys")
    }

    val keys =
      try {
        res.body<JsonWebKeys>().keys
      } catch (e: Exception) {
        LOG.error("Unable to validate token signature; Unable to parse public keys")
        throw AppError.internal("Unable to validate token signature; Unable to parse public keys")
      }

    val publicKey =
      keys.find { it.kid == kid }
        ?: throw AppError.api(
          ErrorReason.Authentication,
          "Unable to validate token signature; Unable to match public key!",
        )

    val modulus = Base64.getUrlDecoder().decode(publicKey.n)
    val exponent = Base64.getUrlDecoder().decode(publicKey.e)
    val spec = RSAPublicKeySpec(BigInteger(1, modulus), BigInteger(1, exponent))

    return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
  }
}
