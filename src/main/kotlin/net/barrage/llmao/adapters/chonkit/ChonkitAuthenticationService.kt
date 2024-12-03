package net.barrage.llmao.adapters.chonkit

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.barrage.llmao.adapters.chonkit.dto.ChonkitAuthentication
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

internal val LOG = io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.app.auth.chonkit")

class ChonkitAuthenticationService
/**
 * Module used to generate JWTs to enable client access to Chonkit.
 *
 * @param client The Ktor client to use for requests.
 * @param key The name of the encryption key to be used in Vault's Transit engine
 * @param jwtConfig Token creation configuration.
 * @param repository Repository to use for storing refresh tokens.
 */
private constructor(
  private val client: HttpClient,
  private val key: String,
  private val jwtConfig: JwtConfig,
  private val repository: ChonkitAuthenticationRepository,
) {
  private val transitPath = "/v1/llmao-transit-engine"

  companion object {
    /** Initialize an instance of `ChonkitAuthenticationService` and log in to Vault. */
    suspend fun init(
      repository: ChonkitAuthenticationRepository,
      config: ApplicationConfig,
    ): ChonkitAuthenticationService {
      LOG.info("Attempting to log in to Vault")

      // The URL of the Vault server
      val endpoint = config.string("vault.endpoint")

      // The AppRole Role ID for Vault authentication
      val roleId = config.string("vault.roleId")

      // The AppRole Secret ID for Vault authentication
      val secretId = config.string("vault.secretId")

      // The name of the encryption key to be used in Vault's Transit engine
      val vaultKey = config.string("vault.keyName")

      val jwtIssuer = config.string("chonkit.jwt.issuer")
      val jwtAudience = config.string("chonkit.jwt.audience")
      val jwtAccessTokenDurationSeconds =
        config.string("chonkit.jwt.accessTokenDurationSeconds").toLong()
      val jwtRefreshTokenDurationSeconds =
        config.string("chonkit.jwt.refreshTokenDurationSeconds").toLong()

      val jwtConfig =
        JwtConfig(
          jwtIssuer,
          jwtAudience,
          jwtAccessTokenDurationSeconds,
          jwtRefreshTokenDurationSeconds,
        )

      val tempClient =
        HttpClient(Apache) {
          install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          defaultRequest {
            url {
              protocol = URLProtocol.HTTPS
              host = endpoint
            }
          }
        }

      val response =
        tempClient.post("/v1/auth/approle/login") {
          contentType(ContentType.Application.Json)
          setBody(mapOf("role_id" to roleId, "secret_id" to secretId))
        }

      if (response.status != HttpStatusCode.OK) {
        LOG.error("Failed to log in to Vault: {}", response.body<String>())
        throw Exception("Failed to log in to Vault")
      }

      val body = response.body<VaultAuthResponse>()
      val token = body.auth.clientToken

      LOG.info("Successfully logged in to Vault")

      val client =
        HttpClient(Apache) {
          install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          defaultRequest {
            url {
              protocol = URLProtocol.HTTPS
              host = endpoint
            }
            header("X-Vault-Token", token)
          }
        }

      return ChonkitAuthenticationService(client, vaultKey, jwtConfig, repository)
    }
  }

  /**
   * Call Vault to authenticate the user and generate a JWT. Should only be called after the user
   * has been authenticated. If an existing refresh token is provided, it will be invalidated.
   *
   * @param user The user to generate a JWT for.
   * @param existingRefreshToken An existing refresh token to invalidate. Used when refreshing.
   */
  suspend fun authenticate(
    user: User,
    existingRefreshToken: String? = null,
  ): ChonkitAuthentication {
    if (user.role != Role.ADMIN) {
      throw AppError.api(ErrorReason.Authentication, "Insufficient permissions")
    }

    if (existingRefreshToken != null) {
      val amount = repository.removeSingleSession(user.id, existingRefreshToken)
      LOG.debug("Removed {} chonkit session(s) for user '{}'", amount, user.id)
    }

    val accessToken = generateToken(user)

    val refreshToken = generateRefreshToken()

    repository.insertNewSession(
      user.id,
      refreshToken,
      key,
      accessToken.keyVersion,
      Instant.now().plusSeconds(jwtConfig.refreshTokenDurationSeconds).atOffset(ZoneOffset.UTC),
    )

    LOG.info("Successfully created chonkit access token for user '${user.id}'")

    return ChonkitAuthentication(accessToken.token, refreshToken)
  }

  suspend fun refresh(user: User, refreshToken: String): ChonkitAuthentication {
    if (user.role != Role.ADMIN) {
      throw AppError.api(ErrorReason.Authentication, "Insufficient permissions")
    }

    LOG.info("Refreshing token for user '${user.id}'")

    repository.getActiveSession(user.id, refreshToken)
      ?: throw AppError.api(ErrorReason.Authentication, "Invalid refresh token")

    return authenticate(user, refreshToken)
  }

  fun logout(userId: KUUID, refreshToken: String, purge: Boolean) {
    if (purge) {
      repository.removeAllSessions(userId)
    } else {
      repository.removeSingleSession(userId, refreshToken)
    }
  }

  private suspend fun generateToken(user: User): ChonkitToken {
    LOG.info("Generating chonkit token for user '${user.id}'")

    val version = getLatestKeyVersion()
    val jwt = constructJwt(user, version)

    return signJwt(jwt)
  }

  private suspend fun getLatestKeyVersion(): String {
    val keyResponse =
      client.get("$transitPath/keys/$key") { contentType(ContentType.Application.Json) }

    if (keyResponse.status != HttpStatusCode.OK) {
      LOG.error("Failed to retrieve signing key: {}", keyResponse.body<String>())
      throw Exception("Failed to retrieve signing key metadata from Vault")
    }

    val keys = keyResponse.body<VaultKeyResponse>().data.keys.keys
    val latest =
      keys.maxOfOrNull { it.toIntOrNull() ?: Int.MIN_VALUE }
        ?: throw Exception("No keys found in Vault")

    return "v$latest"
  }

  private fun constructJwt(user: User, keyVersion: String): Jwt {
    val header = JwtHeader()
    val payload =
      JwtPayload(
        sub = user.email,
        iss = jwtConfig.issuer,
        aud = jwtConfig.audience,
        iat = Instant.now().toEpochMilli() / 1000,
        nbf = Instant.now().toEpochMilli() / 1000,
        exp = Instant.now().plusSeconds(jwtConfig.accessTokenDurationSeconds).toEpochMilli() / 1000,
        role = user.role.name,
        version = keyVersion,
      )

    return Jwt(header, payload)
  }

  private suspend fun signJwt(jwt: Jwt): ChonkitToken {
    val tokenString = jwt.toUnsignedToken()

    // Vault expects the token to be Base64 encoded.
    val signatureData = Base64.getEncoder().encodeToString(tokenString.toByteArray())

    // The payload is always signed with the latest key.
    val response =
      client.post("$transitPath/sign/$key") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("input" to signatureData))
      }

    if (response.status != HttpStatusCode.OK) {
      LOG.error("Failed to sign JWT: {}", response.body<String>())
      throw Exception("Failed to sign JWT")
    }

    val signed = response.body<VaultSignatureResponse>()
    val signature = signed.data.signature

    // Every signature coming from the transit engine starts with the prefix
    // "vault:<KEY_VERSION>:". We don't necessarily need to strip it, but it's OK
    // to leave h4x0r5 wondering why their attempts are failing
    val prefix = "vault:${jwt.payload.version}:"
    val strippedSignature = signature.replace(prefix, "")

    return ChonkitToken("$tokenString.$strippedSignature", jwt.payload.version)
  }

  /** Generate a random refresh token of 2048 bits. */
  private fun generateRefreshToken(): String {
    val random = SecureRandom()

    val bytes = ByteArray(512) // 2048 bits

    random.nextBytes(bytes)

    val encodedBytes = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    return encodedBytes
  }
}

/** Holds the access token and the version of the key used to sign the token. */
@Serializable data class ChonkitToken(val token: String, val keyVersion: String)

@Serializable
data class VaultAuthResponse(val auth: Auth) {
  @Serializable
  data class Auth(
    val renewable: Boolean,
    @SerialName("lease_duration") val leaseDuration: Int,
    val metadata: Map<String, String> = emptyMap(),
    val policies: List<String>,
    val accessor: String,
    @SerialName("client_token") val clientToken: String,
  )
}

@Serializable
data class VaultKeyResponse(val data: KeyData) {
  @Serializable
  data class KeyData(
    val type: String,
    @SerialName("deletion_allowed") val deletionAllowed: Boolean,
    val derived: Boolean,
    val exportable: Boolean,
    @SerialName("allow_plaintext_backup") val allowPlaintextBackup: Boolean,

    /** Keys are the version numbers and the values are key metadata. */
    val keys: Map<String, VaultKey>,
    @SerialName("min_decryption_version") val minDecryptionVersion: Int,
    @SerialName("min_encryption_version") val minEncryptionVersion: Int,
    val name: String,
    @SerialName("supports_encryption") val supportsEncryption: Boolean,
    @SerialName("supports_decryption") val supportsDecryption: Boolean,
    @SerialName("supports_derivation") val supportsDerivation: Boolean,
    @SerialName("supports_signing") val supportsSigning: Boolean,
    val imported: Boolean? = null,
  )
}

@Serializable
data class VaultKey(
  @SerialName("certificate_chain") val certificateChain: String,
  @SerialName("creation_time") val creationTime: String,
  @SerialName("public_key") val publicKey: String,
  val name: String,
)

@Serializable
data class VaultSignatureResponse(val data: SignatureData) {
  @Serializable data class SignatureData(val signature: String)
}
