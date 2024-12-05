package net.barrage.llmao.app.auth.apple

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
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.*
import net.barrage.llmao.app.auth.JsonWebKeys
import net.barrage.llmao.core.auth.AuthenticationProvider
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.auth.LoginSource
import net.barrage.llmao.core.auth.UserInfo
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.auth.apple")

class AppleAuthenticationProvider(
  private val client: HttpClient,
  private val endpoint: String,
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

    val res = client.submitForm(url = "$endpoint/auth/token", params, encodeInQuery = false)

    if (res.status != HttpStatusCode.OK) {
      val errorBody = res.bodyAsText()
      LOG.error("Failed to retrieve token: ${res.status}. Response: $errorBody")
      throw AppError.api(ErrorReason.Authentication, "Failed to retrieve token: ${res.status}")
    }

    val response = res.body<AppleTokenResponse>()
    val idToken = JWT.decode(response.idToken)

    verifyJwtSignature(idToken)

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

  private suspend fun verifyJwtSignature(jwt: DecodedJWT) {
    val key = getPublicKey(jwt.keyId)

    val algorithm = Algorithm.RSA256(key, null)

    val verifier =
      JWT.require(algorithm)
        .withIssuer("https://appleid.apple.com")
        .withAnyOfAudience(clientId, serviceId)
        .build()

    try {
      verifier.verify(jwt)
    } catch (e: Exception) {
      throw AppError.api(ErrorReason.Authentication, "Invalid token")
    }
  }

  private suspend fun getPublicKey(kid: String): RSAPublicKey {
    val res = client.get("$endpoint/auth/keys")
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

  private fun generateToken(subject: String): String {
    val keyBytes = Base64.getDecoder().decode(clientSecret)
    val encodedKey = PKCS8EncodedKeySpec(keyBytes)

    val keyFactory = KeyFactory.getInstance("EC")
    val privateKey = keyFactory.generatePrivate(encodedKey) as ECPrivateKey

    val now = Instant.now()
    return JWT.create()
      .withIssuer(teamId)
      .withAudience(endpoint)
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
