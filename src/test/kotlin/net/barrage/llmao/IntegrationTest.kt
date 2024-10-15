package net.barrage.llmao

import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.testing.*
import java.util.*

open class IntegrationTest(usePostgres: Boolean = true, useWeaviate: Boolean = false) {
  val postgres: TestPostgres? = if (usePostgres) TestPostgres() else null
  val weaviate: TestWeaviate? = if (useWeaviate) TestWeaviate() else null
  var cfg = YamlConfigLoader().load("application.yaml")!!
  val cookieName = cfg.property("session.cookieName").getString()

  fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    try {
      postgres?.let {
        println("POSTGRES START")
        println(it.container.jdbcUrl)
        cfg =
          cfg.mergeWith(
            MapApplicationConfig(
              "db.url" to it.container.jdbcUrl,
              "db.user" to it.container.username,
              "db.password" to it.container.password,
            )
          )
      }
      weaviate?.let {
        cfg =
          cfg.mergeWith(
            MapApplicationConfig(
              "weaviate.host" to it.container.httpHostAddress,
              "weaviate.scheme" to "http",
            )
          )
      }
      environment { config = cfg }
    } catch (e: Exception) {
      e.printStackTrace()
      throw e
    }
    block()
  }

  fun sessionCookie(sessionId: UUID): String = "$cookieName=id%3D%2523s$sessionId"
}

// suspend fun HttpClient.getAuthenticated(url: String, sessionId: KUUID) {
//  get(url) { header(HttpHeaders.Cookie, sessionCookie(sessionId)) }
// }
