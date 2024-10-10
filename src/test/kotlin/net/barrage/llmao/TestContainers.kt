package net.barrage.llmao

import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.schema.model.Property
import io.weaviate.client.v1.schema.model.WeaviateClass
import java.time.OffsetDateTime
import java.util.*
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.barrage.llmao.core.models.*
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.records.SessionsRecord
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.SESSIONS
import net.barrage.llmao.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.weaviate.WeaviateContainer

class TestPostgres {
  val container: PostgreSQLContainer<*> =
    PostgreSQLContainer("postgres:latest")
      .apply {
        withDatabaseName("test")
        withUsername("test")
        withPassword("test")
      }
      .waitingFor(Wait.defaultWaitStrategy())

  val dslContext: DSLContext

  init {
    container.start()

    val dataSource =
      PGSimpleDataSource().apply {
        setURL(container.jdbcUrl)
        password = "test"
        user = "test"
        databaseName = "test"
      }

    Liquibase(
        "db/changelog.yaml",
        ClassLoaderResourceAccessor(),
        JdbcConnection(dataSource.connection),
      )
      .update("main")

    dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)
  }

  fun testUser(email: String = "test@user.me", admin: Boolean, active: Boolean = true): User {
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
      .values(email, "Test", "Test", "Test", if (admin) "ADMIN" else "USER", active)
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }

  fun deleteTestUser(id: UUID) {
    dslContext.deleteFrom(USERS).where(USERS.ID.eq(id)).execute()
  }

  fun testSession(userId: UUID): Session {
    return dslContext
      .insertInto(SESSIONS)
      .columns(SESSIONS.USER_ID, SESSIONS.EXPIRES_AT)
      .values(userId, OffsetDateTime.now().plusDays(1))
      .returning()
      .fetchOne(SessionsRecord::toSessionData)!!
  }

  fun testAgent(
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
      .fetchOne(AgentsRecord::toAgent)!!
  }
}

class TestWeaviate {
  val container: WeaviateContainer =
    WeaviateContainer("cr.weaviate.io/semitechnologies/weaviate:1.25.5")
  val client: WeaviateClient

  init {
    container.start()
    client = WeaviateClient(Config("http", container.httpHostAddress))
  }

  fun insertTestCollection(name: String = "TestClass") {
    val contentProperty =
      Property.builder()
        .name("content")
        .description("Document content")
        .dataType(listOf("text"))
        .build()

    val newClass =
      WeaviateClass.builder()
        .className(name)
        .description("Test vector collection")
        .properties(listOf(contentProperty))
        .build()

    client.schema().classCreator().withClass(newClass).run()
  }
}
