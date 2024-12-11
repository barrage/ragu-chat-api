package net.barrage.llmao.app.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.app.auth.JsonWebKey")

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

suspend fun getPublicKey(client: HttpClient, keysEndpoint: String, kid: String): RSAPublicKey {
  val res = client.get(keysEndpoint)
  if (res.status != HttpStatusCode.OK) {
    LOG.error("Unable to validate token signature; Unable to retrieve public keys")
    throw AppError.internal("Unable to validate token signature; Unable to retrieve public keys")
  }

  val keys =
    try {
      res.body<JsonWebKeys>().keys
    } catch (_: Exception) {
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
