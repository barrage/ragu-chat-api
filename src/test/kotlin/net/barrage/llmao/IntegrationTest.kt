package net.barrage.llmao

import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.*
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.ServiceState
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(
  TestInstance.Lifecycle.PER_CLASS // Needed to avoid static methods in companion object
)
open class IntegrationTest(
  /** If `true`, initialize the database container and the JOOQ client. */
  private val usePostgres: Boolean = true,

  /** If `true`, initialize the weaviate container and the weaviate client. */
  private val useWeaviate: Boolean = false,

  /**
   * If `true`, start a wiremock container for the OpenAI API. All requests to the OpenAI API will
   * be sent to the mock instance instead.
   */
  private val useWiremockOpenAi: Boolean = false,

  /**
   * If given and `useWiremockOpenAi` is `true`, an existing wiremock container will be used
   * instead, located on the URL. Useful for recording responses from test suites.
   */
  private val wiremockUrlOverride: String? = null,
) {
  var postgres: TestPostgres? = null
  var weaviate: TestWeaviate? = null
  var openAiApi: OpenAiWiremock? = null
  var services: ServiceState? = null

  private var cfg = YamlConfigLoader().load("application.yaml")!!
  private val cookieName = cfg.property("session.cookieName").getString()

  @BeforeAll
  fun beforeAll() {
    if (usePostgres) {
      loadPostgres()
    }

    if (useWeaviate) {
      loadWeaviate()
    }

    if (useWiremockOpenAi) {
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
    openAiApi = OpenAiWiremock()
    val url = wiremockUrlOverride ?: openAiApi!!.container.baseUrl
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          // Has to match the URL from the OpenAI SDK
          "llm.openAi.endpoint" to "${url}/v1/"
        )
      )

    if (wiremockUrlOverride == null) {
      cfg =
        cfg.mergeWith(
          MapApplicationConfig("llm.openAi.apiKey" to "super-duper-secret-openai-api-key")
        )
    }
  }

  /**
   * The main test execution function that uses configuration obtained from the test containers as
   * the application environment.
   */
  fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    val azureEndpoint = cfg.property("llm.azure.endpoint").getString()

    environment { config = cfg }

    externalServices { hosts("https://$azureEndpoint.openai.azure.com") { routing {} } }

    block()
  }

  fun sessionCookie(sessionId: UUID): String = "$cookieName=id%3D%2523s$sessionId"
}
