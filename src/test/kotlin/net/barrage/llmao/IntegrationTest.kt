package net.barrage.llmao

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.testing.*
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.CHONKIT_AUTH_FEATURE_FLAG
import net.barrage.llmao.app.WHATSAPP_FEATURE_FLAG
import net.barrage.llmao.app.api.ws.ClientMessageSerializer
import net.barrage.llmao.core.EventListener
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.wiremock.extension.jwt.JwtExtensionFactory

@TestInstance(
  TestInstance.Lifecycle.PER_CLASS // Needed to avoid static methods in companion object
)
open class IntegrationTest(
  /** If `true`, initialize the weaviate container and the weaviate client. */
  private val useWeaviate: Boolean = false,

  /** If `true`, start a wiremock container for all external APIs. */
  private val useWiremock: Boolean = false,

  /** Enabled OAuth providers. */
  oAuthProviders: List<String> = listOf("google", "apple", "carnet"),

  /**
   * If given and `useWiremock` is `true`, an existing wiremock container will be used instead,
   * located on the URL. Useful for recording responses from test suites. Ideally this would be an
   * environment variable, but we do not live in an ideal world.
   */
  private val wiremockUrlOverride: String? = null,

  /** If `true`, enables the Chonkit authentication module. */
  enableChonkitAuth: Boolean = false,
  enableWhatsApp: Boolean = false,
) {
  val postgres: TestPostgres = TestPostgres()
  var minio: TestMinio = TestMinio()
  var weaviate: TestWeaviate? = null
  var wiremock: WireMockServer? = null
  lateinit var app: ApplicationState

  private var cfg = ConfigLoader.load("application.example.conf")

  private val applicationStoppingJob: CompletableJob = Job()

  init {
    // Postgres
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "db.url" to postgres.container.jdbcUrl,
          "db.user" to postgres.container.username,
          "db.password" to postgres.container.password,
          "db.r2dbcHost" to postgres.container.host,
          "db.r2dbcPort" to postgres.container.getMappedPort(5432).toString(),
          "db.r2dbcDatabase" to postgres.container.databaseName,
          "db.runMigrations" to "false", // We migrate manually on PG container initialization
          "oauth.apple.clientSecret" to generateP8PrivateKey(),
        )
      )

    // Minio
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "minio.endpoint" to minio.container.s3URL,
          "minio.accessKey" to "testMinio",
          "minio.secretKey" to "testMinio",
          "minio.bucket" to "test",
        )
      )

    // Feature adapters
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          CHONKIT_AUTH_FEATURE_FLAG to enableChonkitAuth.toString(),
          WHATSAPP_FEATURE_FLAG to enableWhatsApp.toString(),
          *oAuthProviders.map { "ktor.features.oauth.$it" to "true" }.toTypedArray(),
        )
      )
  }

  /**
   * The main test execution function that uses configuration obtained from the test containers as
   * the application environment.
   */
  fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    environment { config = cfg }
    try {
      block()
    } catch (e: Throwable) {
      e.printStackTrace()
      throw e
    }
  }

  /** Main execution function for websocket tests that exposes a websocket client. */
  fun wsTest(block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) = testApplication {
    environment { config = cfg }
    val client = createClient {
      install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
      }
    }
    block(client)
  }

  @BeforeAll
  fun beforeAll() {
    if (useWeaviate) {
      loadWeaviate()
    }

    if (useWiremock) {
      loadWiremock()
    }

    app = ApplicationState(cfg, applicationStoppingJob, EventListener())
  }

  @AfterAll
  fun stopContainersAfterTests() {
    postgres.container.stop()
    weaviate?.container?.stop()
  }

  private fun loadWeaviate() {
    weaviate = TestWeaviate()
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "weaviate.host" to weaviate!!.container.httpHostAddress,
          "weaviate.scheme" to "http",
        )
      )
  }

  private fun loadWiremock() {
    if (wiremockUrlOverride != null) {
      cfg =
        cfg.mergeWith(
          MapApplicationConfig(
            // We are not overriding the API key since we need real ones.

            // Has to match the URL from the OpenAI SDK.
            "llm.openai.endpoint" to "$wiremockUrlOverride/v1/",
            "embeddings.openai.endpoint" to "$wiremockUrlOverride/v1/",

            // Has to match the URL from the Azure OpenAI SDK.
            "embeddings.azure.endpoint" to "wiremockUrlOverride/openai/deployments",
            "vault.endpoint" to wiremockUrlOverride,

            // Has to match the URL from the Infobip SDK.
            "infobip.endpoint" to wiremockUrlOverride,
          )
        )
      return
    }

    /**
     * Creates Wiremock server locally.
     *
     * The `resources/wiremock/mappings` directory contains the definition for responses we mock.
     *
     * Each response will have a `bodyFileName` in the response designating the body for it. When
     * the server is started it will copy `resources/wiremock/__files` directory to the
     * `/home/wiremock/__files` directory onto the server, matching the directory structure. The
     * `bodyFileName` must be equal to the path from the `__files` directory.
     */
    wiremock =
      WireMockServer(
        WireMockConfiguration.options()
          .dynamicPort()
          .extensions(JwtExtensionFactory())
          .usingFilesUnderClasspath("wiremock")
      )

    wiremock!!.start()
    val url = wiremock!!.baseUrl()

    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "llm.openai.endpoint" to "$url/$OPENAI_WM/v1/",
          "llm.openai.apiKey" to "super-duper-secret-openai-api-key",
          "embeddings.openai.endpoint" to "$url/$OPENAI_WM/v1/",
          "embeddings.openai.apiKey" to "super-duper-secret-openai-api-key",
          "embeddings.azure.endpoint" to "$url/$AZURE_WM/openai/deployments",
          "embeddings.azure.apiKey" to "super-duper-secret-azure-api-key",
          "embeddings.fembed.endpoint" to "$url/$FEMBED_WM",
          "vault.endpoint" to "$url/$VAULT_WM",
          "infobip.endpoint" to url,
          "infobip.apiKey" to "super-duper-secret-infobip-api-key",
          "oauth.apple.tokenEndpoint" to "$url/$APPLE_VM/auth/token",
          "oauth.apple.keysEndpoint" to "$url/$APPLE_VM/auth/keys",
          "oauth.apple.clientId" to "clientId",
          "oauth.apple.serviceId" to "serviceId",
          "oauth.google.tokenEndpoint" to "$url/$GOOGLE_WM/auth/token",
          "oauth.google.keysEndpoint" to "$url/$GOOGLE_WM/auth/keys",
          "oauth.google.clientId" to "aud.apps.googleusercontent.com",
          "oauth.carnet.tokenEndpoint" to "$url/$CARNET_WM/auth/token",
          "oauth.carnet.keysEndpoint" to "$url/$CARNET_WM/auth/jwks",
          "oauth.carnet.userInfoEndpoint" to "$url/$CARNET_WM/auth/userinfo",
          "oauth.carnet.logoutEndpoint" to "$url/$CARNET_WM/auth/logout",
          "llm.ollama.endpoint" to "$url/$OLLAMA_WM",
        )
      )
  }
}

/** Has to be the same name as `session.cookieName`. */
fun sessionCookie(sessionId: UUID): String = "kappi=id%3D%2523s$sessionId"

// Wiremock URL discriminators

const val OPENAI_WM = "__openai"
const val AZURE_WM = "__azure"
const val FEMBED_WM = "__fembed"
const val VAULT_WM = "__vault"
const val GOOGLE_WM = "__google"
const val APPLE_VM = "__apple"
const val CARNET_WM = "__carnet"
const val OLLAMA_WM = "__ollama"

// Wiremock response triggers

/** Prompt configured to make wiremock return a completion response without a stream. */
const val COMPLETIONS_COMPLETION_PROMPT = "v1_chat_completions_completion"

/** Prompt configured to make wiremock return the default stream response. */
const val COMPLETIONS_STREAM_PROMPT = "v1_chat_completions_stream"

/**
 * Prompt configured to make wiremock return a stream response with additional whitespace for
 * testing purposes.
 */
const val COMPLETIONS_STREAM_WHITESPACE_PROMPT = "v1_chat_completions_whitespace_stream"

/** Prompt configured to make wiremock return a stream response with a long response. */
const val COMPLETIONS_STREAM_LONG_PROMPT = "v1_chat_completions_long_stream"

// Wiremock message content

/** Returned on [COMPLETIONS_COMPLETION_PROMPT]. */
const val COMPLETIONS_RESPONSE = "v1_chat_completions_completion_response"

/** Wiremock response for a stream response. */
const val COMPLETIONS_TITLE_RESPONSE = "v1_chat_completions_title_response"

/** Returned on [COMPLETIONS_STREAM_PROMPT]. */
const val COMPLETIONS_STREAM_RESPONSE = "v1_chat_completions_stream_response"

/** Returned on [COMPLETIONS_STREAM_WHITESPACE_PROMPT]. */
const val COMPLETIONS_STREAM_WHITESPACE_RESPONSE =
  "v1_chat_completions_stream_response\nwith whitespace"

val COMPLETIONS_STREAM_LONG_RESPONSE = COMPLETIONS_STREAM_RESPONSE.repeat(4)

private fun generateP8PrivateKey(): String {
  val keyPairGen = KeyPairGenerator.getInstance("EC")
  val ecSpec = ECGenParameterSpec("secp256r1")
  keyPairGen.initialize(ecSpec)
  val keyPair = keyPairGen.generateKeyPair()

  val privateKey: PrivateKey = keyPair.private

  val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(privateKey.encoded)

  return Base64.getEncoder().encodeToString(pkcs8EncodedKeySpec.encoded)
}
