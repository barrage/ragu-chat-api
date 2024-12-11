package net.barrage.llmao.core.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.*
import net.barrage.llmao.app.auth.getPublicKey
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

interface AuthenticationProvider {
  /** Return this provider's unique identifier. */
  fun id(): String

  /** Authenticate a user, returning their info upon successful authentication. */
  suspend fun authenticate(payload: LoginPayload): UserInfo

  suspend fun verifyJwtSignature(
    client: HttpClient,
    jwt: DecodedJWT,
    keysEndpoint: String,
    tokenIssuer: String,
    audience: Array<String>,
  ) {
    val key = getPublicKey(client, keysEndpoint, jwt.keyId)
    val algorithm = Algorithm.RSA256(key, null)
    val verifier =
      JWT.require(algorithm).withIssuer(tokenIssuer).withAnyOfAudience(*audience).build()

    try {
      verifier.verify(jwt)
    } catch (e: Exception) {
      throw AppError.api(ErrorReason.Authentication, "Invalid token")
    }
  }
}
