package net.barrage.llmao.app.auth.apple

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*
import net.barrage.llmao.core.auth.AuthenticationProvider
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.auth.LoginSource
import net.barrage.llmao.core.auth.UserInfo
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.auth.apple")

class AppleAuthenticationProvider(
  private val client: HttpClient,
  private val tokenEndpoint: String,
  private val keysEndpoint: String,
  private val tokenIssuer: String,
  private val clientId: String,
  private val serviceId: String,
  private val teamId: String,
  private val keyId: String,
  private val clientSecret: String,
) : AuthenticationProvider {
  private var clientToken: String = generateToken(clientId)
  private var serviceToken: String = generateToken(serviceId)

  override fun id(): String {
    return "apple"
  }

  override suspend fun authenticate(payload: LoginPayload): UserInfo {
    val (cid, token) =
      if (payload.source == LoginSource.IOS) {
        if (expiredToken(clientToken)) {
          clientToken = this.generateToken(clientId)
        }
        Pair(clientId, clientToken)
      } else {
        if (expiredToken(serviceToken)) {
          serviceToken = this.generateToken(serviceId)
        }
        Pair(serviceId, serviceToken)
      }

    val params =
      Parameters.build {
        append("code", payload.code)
        append("redirect_uri", payload.redirectUri)
        append("grant_type", payload.grantType)
        append("client_id", cid)
        append("client_secret", token)
        append("code_verifier", payload.codeVerifier)
      }

    val res = client.submitForm(url = tokenEndpoint, params, encodeInQuery = false)

    if (res.status != HttpStatusCode.OK) {
      val errorBody = res.bodyAsText()
      LOG.error("Failed to retrieve token: ${res.status}. Response: $errorBody")
      throw AppError.api(ErrorReason.Authentication, "Failed to retrieve token: ${res.status}")
    }

    val response = res.body<AppleTokenResponse>()
    val idToken = JWT.decode(response.idToken)

    verifyJwtSignature(client, idToken, keysEndpoint, tokenIssuer, arrayOf(clientId, serviceId))

    val appleId = idToken.subject

    if (idToken.expiresAtAsInstant.isBefore(Instant.now())) {
      throw AppError.api(ErrorReason.Authentication, "Token expired")
    }

    val email =
      idToken.getClaim("email")?.asString().let {
        if (it.isNullOrBlank()) {
          throw AppError.api(ErrorReason.Authentication, "Email not found")
        }
        it
      }

    val isVerified = idToken.getClaim("email_verified")?.asBoolean() == true
    if (!isVerified) {
      throw AppError.api(ErrorReason.Authentication, "Email not verified")
    }

    return UserInfo(id = appleId, email = email)
  }

  private fun generateToken(subject: String): String {
    val keyBytes = Base64.getDecoder().decode(clientSecret)
    val encodedKey = PKCS8EncodedKeySpec(keyBytes)

    val keyFactory = KeyFactory.getInstance("EC")
    val privateKey = keyFactory.generatePrivate(encodedKey) as ECPrivateKey

    val now = Instant.now()
    return JWT.create()
      .withIssuer(teamId)
      .withAudience(tokenIssuer)
      .withSubject(subject)
      .withIssuedAt(now)
      .withExpiresAt(now.plusSeconds(30 * 24 * 3600)) // expires in 30 days
      .withClaim("alg", "ES256")
      .withKeyId(keyId)
      .sign(Algorithm.ECDSA256(privateKey))
  }

  private fun expiredToken(token: String): Boolean {
    return JWT.decode(token).expiresAtAsInstant.isBefore(Instant.now())
  }
}
