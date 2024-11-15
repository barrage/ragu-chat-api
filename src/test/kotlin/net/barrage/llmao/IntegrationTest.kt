package net.barrage.llmao

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.request.*
import io.ktor.server.response.*
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
  private val useWiremockOpenAi: Boolean = false,
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
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "llm.openAi.endpoint" to "${openAiApi!!.container.baseUrl}/v1/",
          "llm.openAi.apiKey" to "my-super-duper-secret-openai-api-key",
        )
      )
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
