package net.barrage.llmao.app.adapters.chonkit

import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Jwt(val header: JwtHeader, val payload: JwtPayload) {
  fun toUnsignedToken(): String {
    val json = Json { encodeDefaults = true }

    val headerJson = json.encodeToString(header)
    val payloadJson = json.encodeToString(payload)

    val headerEncoded = Base64.getUrlEncoder().encodeToString(headerJson.toByteArray())
    val payloadEncoded = Base64.getUrlEncoder().encodeToString(payloadJson.toByteArray())

    return "$headerEncoded.$payloadEncoded"
  }
}

@Serializable data class JwtHeader(val alg: String = "RS256", val typ: String = "JWT")

@Serializable
data class JwtPayload(
  // Standard fields.

  /** Issuer. */
  val iss: String,

  /** Subject, i.e. the user ID. */
  val sub: String,

  /** Audience, i.e. which service the token is intended for. In our case it's always `chonkit`. */
  val aud: String,

  /** Issued at. */
  val iat: Long,

  /** Expiration. */
  val exp: Long,

  /** Not before. */
  val nbf: Long,

  // Custom fields.

  /** User role. */
  val role: String,

  /** Version of the key used to sign the token. */
  val version: Int,
)

data class JwtConfig(
  val issuer: String,
  val audience: String,
  val accessTokenDurationSeconds: Long,
  val refreshTokenDurationSeconds: Long,
)
