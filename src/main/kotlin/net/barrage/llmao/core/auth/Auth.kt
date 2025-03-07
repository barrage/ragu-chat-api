package net.barrage.llmao.core.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.auth.AuthenticationProvider")

class Auth(
  private val client: HttpClient,

  /** Maps `kid`s to their respective public keys. */
  private val keys: Map<String, RSAPublicKey>,
) {
  companion object {
    suspend fun init(client: HttpClient, discoveryEndpoint: String): Auth {
      val response = client.get(discoveryEndpoint)
      val discovery = response.body<Discovery>()
      val keysResponse = client.get(discovery.jwksUri)
      val jwks = keysResponse.body<JsonWebKeys>()

      val keys =
        jwks.keys.fold(mutableMapOf<String, RSAPublicKey>()) { acc, key ->
          val modulus = Base64.getUrlDecoder().decode(key.n)
          val exponent = Base64.getUrlDecoder().decode(key.e)
          val spec = RSAPublicKeySpec(BigInteger(1, modulus), BigInteger(1, exponent))
          val publicKey = KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
          acc[key.kid] = publicKey
          acc
        }

      return Auth(client, keys)
    }
  }

  fun verifyJwtSignature(jwt: DecodedJWT, tokenIssuer: String, audience: Array<String>) {
    val key = keys[jwt.keyId]
    val algorithm = Algorithm.RSA256(key, null)
    val verifier =
      JWT.require(algorithm)
        .withIssuer(tokenIssuer)
        .withAnyOfAudience(*audience)
        .acceptLeeway(30)
        .build()

    try {
      verifier.verify(jwt)
    } catch (e: Exception) {
      LOG.error("Invalid token", e)
      throw AppError.api(ErrorReason.Authentication, "Invalid token")
    }
  }
}

@Serializable
private data class Discovery(
  val issuer: String,
  @SerialName("authorization_endpoint") val authorizationEndpoint: String,
  @SerialName("token_endpoint") val tokenEndpoint: String,
  @SerialName("jwks_uri") val jwksUri: String,
)

@Serializable
data class JsonWebKey(
  val kty: String,
  val kid: String,
  val use: String,
  val alg: String,
  val n: String,
  val e: String,
)

@Serializable class JsonWebKeys(val keys: List<JsonWebKey>)
