package net.barrage.llmao

import io.ktor.server.config.*
import io.ktor.server.config.yaml.*
import io.ktor.server.testing.*
import java.time.OffsetDateTime
import java.util.*
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.toSessionData
import net.barrage.llmao.tables.records.SessionsRecord
import net.barrage.llmao.tables.references.*
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.AfterClass
import org.junit.BeforeClass
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

open class LlmaoTestClass {
  companion object {
    private lateinit var databaseContainer: PostgreSQLContainer<*>
    lateinit var engine: TestApplicationEngine
    lateinit var dslContext: DSLContext

    @BeforeClass
    @JvmStatic
    fun setup() {
      databaseContainer =
        PostgreSQLContainer("postgres:latest")
          .apply {
            withDatabaseName("test")
            withUsername("test")
            withPassword("test")
          }
          .waitingFor(Wait.defaultWaitStrategy())

      databaseContainer.start()

      val pgsqlConfig =
        MapApplicationConfig(
          "db.url" to databaseContainer.jdbcUrl,
          "db.user" to databaseContainer.username,
          "db.password" to databaseContainer.password,
        )

      val dataSource =
        PGSimpleDataSource().apply {
          setURL(pgsqlConfig.property("db.url").getString())
          user = pgsqlConfig.property("db.user").getString()
          password = pgsqlConfig.property("db.password").getString()
        }

      val config = YamlConfigLoader().load("config/application.yaml")!!.mergeWith(pgsqlConfig)

      val flyway = Flyway.configure().dataSource(dataSource).validateMigrationNaming(true).load()
      flyway.repair()
      flyway.migrate()

      dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)

      engine = TestApplicationEngine(createTestEnvironment { this.config = config })
      engine.start(wait = false)
    }

    @AfterClass
    @JvmStatic
    fun tearDown() {
      databaseContainer.stop()
      engine.stop(1000, 1000)
    }

    fun createUser(admin: Boolean, active: Boolean = true): User {
      return dslContext
        .insertInto(USERS)
        .columns(
          USERS.EMAIL,
          USERS.FULL_NAME,
          USERS.FIRST_NAME,
          USERS.LAST_NAME,
          USERS.ROLE,
          USERS.ACTIVE,
        )
        .values("test@test.com", "Test", "Test", "Test", if (admin) "ADMIN" else "USER", active)
        .returning()
        .fetchOneInto(User::class.java)!!
    }

    fun createSession(userId: UUID): Session {
      return dslContext
        .insertInto(SESSIONS)
        .columns(SESSIONS.USER_ID, SESSIONS.EXPIRES_AT)
        .values(userId, OffsetDateTime.now().plusDays(1))
        .returning()
        .fetchOne(SessionsRecord::toSessionData)!!
    }

    fun createAgent(
      context: String = "Test",
      llmProvider: String = "ollama",
      model: String = "mistral:latest",
      vectorProvider: String = "weaviate",
      embeddingProvider: String = "fembed",
      embeddingModel: String = "Xenova/bge-large-en-v1.5",
      active: Boolean = true,
    ): Agent {
      return dslContext
        .insertInto(AGENTS)
        .columns(
          AGENTS.NAME,
          AGENTS.DESCRIPTION,
          AGENTS.CONTEXT,
          AGENTS.LLM_PROVIDER,
          AGENTS.MODEL,
          AGENTS.LANGUAGE,
          AGENTS.VECTOR_PROVIDER,
          AGENTS.EMBEDDING_PROVIDER,
          AGENTS.EMBEDDING_MODEL,
          AGENTS.ACTIVE,
        )
        .values(
          "Test",
          context,
          "Test",
          llmProvider,
          model,
          "cro",
          vectorProvider,
          embeddingProvider,
          embeddingModel,
          active,
        )
        .returning()
        .fetchOneInto(Agent::class.java)!!
    }

    fun cleanseTables() {
      dslContext
        .truncate(MESSAGES, FAILED_MESSAGES, CHATS, AGENT_COLLECTIONS, SESSIONS, USERS, AGENTS)
        .execute()
    }

    fun sessionCookie(sessionId: UUID) = "kappi=id%3D%2523s$sessionId"
  }
}
