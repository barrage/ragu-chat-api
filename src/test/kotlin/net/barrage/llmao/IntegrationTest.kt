package net.barrage.llmao

import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.testing.*
import java.util.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(
  TestInstance.Lifecycle.PER_CLASS
) // Needed to avoid static methods in companion object
open class IntegrationTest(
  private val usePostgres: Boolean = true,
  private val useWeaviate: Boolean = false,
) {

  var postgres: TestPostgres? = null
  var weaviate: TestWeaviate? = null

  private var cfg = YamlConfigLoader().load("application.yaml")!!
  private val cookieName = cfg.property("session.cookieName").getString()

  @BeforeAll
  fun beforeAll() {
    if (usePostgres) {
      postgres = TestPostgres()
      postgres!!.container.start()
      cfg =
        cfg.mergeWith(
          MapApplicationConfig(
            "db.url" to postgres!!.container.jdbcUrl,
            "db.user" to postgres!!.container.username,
            "db.password" to postgres!!.container.password,
          )
        )
    }

    if (useWeaviate) {
      weaviate = TestWeaviate()
      weaviate!!.container.start()
      cfg =
        cfg.mergeWith(
          MapApplicationConfig(
            "weaviate.host" to weaviate!!.container.httpHostAddress,
            "weaviate.scheme" to "http",
          )
        )
    }

    cfg = cfg.mergeWith(MapApplicationConfig("db.runMigrations" to "false"))
  }

  fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    environment { config = cfg }
    block()
  }

  fun sessionCookie(sessionId: UUID): String = "$cookieName=id%3D%2523s$sessionId"
}
