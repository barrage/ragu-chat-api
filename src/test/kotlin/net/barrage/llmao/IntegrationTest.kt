package net.barrage.llmao

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.testing.*
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.ServiceState
import net.barrage.llmao.app.api.ws.ClientMessageSerializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(
  TestInstance.Lifecycle.PER_CLASS // Needed to avoid static methods in companion object
)
open class IntegrationTest(
  /** If `true`, initialize the weaviate container and the weaviate client. */
  private val useWeaviate: Boolean = false,

  /** If `true`, start a wiremock container for all external APIs. */
  private val useWiremock: Boolean = false,

  /**
   * If given and `useWiremock` is `true`, an existing wiremock container will be used instead,
   * located on the URL. Useful for recording responses from test suites. Ideally this would be an
   * environment variable, but we do not live in an ideal world.
   */
  private val wiremockUrlOverride: String? = null,
) {
  val postgres: TestPostgres = TestPostgres()
  var weaviate: TestWeaviate? = null
  private var wiremock: Wiremock? = null
  var services: ServiceState? = null

  private var cfg = YamlConfigLoader().load("application.yaml")!!

  init {
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "db.url" to postgres.container.jdbcUrl,
          "db.user" to postgres.container.username,
          "db.password" to postgres.container.password,
          "db.runMigrations" to "false", // We migrate manually on PG container initialization
          "oauth.apple.clientSecret" to generateP8PrivateKey(),
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

    val applicationState = ApplicationState(cfg)
    services = ServiceState(applicationState)
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
            "llm.openai.endpoint" to "${wiremockUrlOverride}/v1/",
            "embeddings.openai.endpoint" to "${wiremockUrlOverride}/v1/",

            // Has to match the URL from the Azure OpenAI SDK.
            "embeddings.azure.endpoint" to "${wiremockUrlOverride}/openai/deployments",
          )
        )
      return
    }

    wiremock = Wiremock()
    val url = wiremock!!.container.baseUrl

    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "llm.openai.endpoint" to "${url}/$OPENAI_WM/v1/",
          "llm.openai.apiKey" to "super-duper-secret-openai-api-key",
          "embeddings.openai.endpoint" to "${url}/$OPENAI_WM/v1/",
          "embeddings.openai.apiKey" to "super-duper-secret-openai-api-key",
          "embeddings.azure.endpoint" to "${url}/${AZURE_WM}/openai/deployments",
          "embeddings.azure.apiKey" to "super-duper-secret-azure-api-key",
          "embeddings.fembed.endpoint" to "${url}/${FEMBED_WM}",
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
