package net.barrage.llmao

import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.testing.*
import java.util.*
import kotlin.test.AfterTest
import org.junit.AfterClass
import org.junit.BeforeClass

open class IntegrationTest {
  fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    environment { config = cfg }
    block()
  }

  fun sessionCookie(sessionId: UUID): String = "$cookieName=id%3D%2523s$sessionId"

  @AfterTest
  fun resetDatabase() {
    postgres.resetPgDatabase()
  }

  companion object {
    lateinit var postgres: TestPostgres
    lateinit var weaviate: TestWeaviate
    var cfg = YamlConfigLoader().load("application.yaml")!!
    val cookieName = cfg.property("session.cookieName").getString()

    @BeforeClass
    @JvmStatic
    fun classSetup() {
      postgres = TestPostgres()
      weaviate = TestWeaviate()
      postgres.container.start()
      weaviate.container.start()
      postgres.let {
        cfg =
          cfg.mergeWith(
            MapApplicationConfig(
              "db.url" to it.container.jdbcUrl,
              "db.user" to it.container.username,
              "db.password" to it.container.password,
            )
          )
      }
      weaviate.let {
        cfg =
          cfg.mergeWith(
            MapApplicationConfig(
              "weaviate.host" to it.container.httpHostAddress,
              "weaviate.scheme" to "http",
            )
          )
      }
    }

    @AfterClass
    @JvmStatic
    fun testClassTeardown() {
      postgres.container.stop()
      weaviate.container.stop()
    }
  }
}
