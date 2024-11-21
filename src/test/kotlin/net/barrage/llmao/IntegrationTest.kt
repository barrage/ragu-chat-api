package net.barrage.llmao

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.testing.*
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
   * If given and `useWiremockOpenAi` is `true`, an existing wiremock container will be used
   * instead, located on the URL. Useful for recording responses from test suites. Ideally this
   * would be an environment variable, but we do not live in an ideal world.
   */
  private val wiremockUrlOverride: String? = null,
) {
  var postgres: TestPostgres? = null
  var weaviate: TestWeaviate? = null
  private var wiremock: Wiremock? = null
  var services: ServiceState? = null

  private var cfg = YamlConfigLoader().load("application.yaml")!!

  /**
   * The main test execution function that uses configuration obtained from the test containers as
   * the application environment.
   */
  fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    environment { config = cfg }
    block()
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
    // Always load postgres because life is easier that way
    loadPostgres()

    if (useWeaviate) {
      loadWeaviate()
    }

    if (useWiremock) {
      loadOpenAiApi()
    }

    val applicationState = ApplicationState(cfg)
    services = ServiceState(applicationState)
  }

  private fun loadPostgres() {
    postgres = TestPostgres()
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "db.url" to postgres!!.container.jdbcUrl,
          "db.user" to postgres!!.container.username,
          "db.password" to postgres!!.container.password,
          "db.runMigrations" to "false", // We migrate manually on PG container initialization
        )
      )
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

  private fun loadOpenAiApi() {
    if (wiremockUrlOverride != null) {
      cfg =
        cfg.mergeWith(
          MapApplicationConfig(
            // We are not overriding the API key since we need real ones.

            // Has to match the URL from the OpenAI SDK.
            "llm.openAi.endpoint" to "${wiremockUrlOverride}/v1/",

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
          "llm.openAi.endpoint" to "${url}/v1/",
          "llm.openAi.apiKey" to "super-duper-secret-openai-api-key",
          "embeddings.azure.endpoint" to "${url}/openai/deployments",
          "embeddings.azure.apiKey" to "super-duper-secret-azure-api-key",
        )
      )
  }
}

/** Has to be the same name as `session.cookieName`. */
fun sessionCookie(sessionId: UUID): String = "kappi=id%3D%2523s$sessionId"

// Wiremock message content

const val COMPLETIONS_RESPONSE = "v1_chat_completions_completion_response"
const val COMPLETIONS_TITLE_RESPONSE = "v1_chat_completions_title_response"
const val COMPLETIONS_STREAM_RESPONSE = "v1_chat_completions_stream_response"
