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
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toAgentConfiguration
import net.barrage.llmao.core.models.toChat
import net.barrage.llmao.core.models.toMessage
import net.barrage.llmao.core.models.toSessionData
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.tables.records.AgentConfigurationsRecord
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.records.SessionsRecord
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.MESSAGES
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

  private lateinit var liquibase: Liquibase

  init {
    container.start()

    val dataSource =
      PGSimpleDataSource().apply {
        setURL(container.jdbcUrl)
        password = "test"
        user = "test"
        databaseName = "test"
      }

    dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    liquibase =
      Liquibase(
        "db/changelog.yaml",
        ClassLoaderResourceAccessor(),
        JdbcConnection(dataSource.connection),
      )
    liquibase.update()
  }

  fun resetPgDatabase() {
    dslContext.execute("DROP SCHEMA public CASCADE")
    dslContext.execute("CREATE SCHEMA public")

    liquibase.update("main")
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

  fun deleteTestAgent(id: UUID) {
    dslContext.deleteFrom(AGENTS).where(AGENTS.ID.eq(id)).execute()
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
    name: String = "Test",
    active: Boolean = true,
    vectorProvider: String = "weaviate",
    embeddingProvider: String = "azure",
    embeddingModel: String = "text-embedding-ada-002",
  ): Agent {
    val agent =
      dslContext
        .insertInto(AGENTS)
        .columns(
          AGENTS.NAME,
          AGENTS.DESCRIPTION,
          AGENTS.ACTIVE,
          AGENTS.VECTOR_PROVIDER,
          AGENTS.EMBEDDING_PROVIDER,
          AGENTS.EMBEDDING_MODEL,
          AGENTS.ACTIVE,
          AGENTS.LANGUAGE,
        )
        .values(
          name,
          "Test",
          active,
          vectorProvider,
          embeddingProvider,
          embeddingModel,
          active,
          "croatian",
        )
        .returning()
        .fetchOne(AgentsRecord::toAgent)!!

    return agent
  }

  fun testAgentConfiguration(
    agentId: UUID,
    version: Int = 1,
    context: String = "Test",
    llmProvider: String = "openai",
    model: String = "gpt-4",
  ): AgentConfiguration {
    val configuration =
      dslContext
        .insertInto(AGENT_CONFIGURATIONS)
        .columns(
          AGENT_CONFIGURATIONS.AGENT_ID,
          AGENT_CONFIGURATIONS.VERSION,
          AGENT_CONFIGURATIONS.CONTEXT,
          AGENT_CONFIGURATIONS.LLM_PROVIDER,
          AGENT_CONFIGURATIONS.MODEL,
        )
        .values(agentId, version, context, llmProvider, model)
        .returning()
        .fetchOne(AgentConfigurationsRecord::toAgentConfiguration)!!

    dslContext
      .update(AGENTS)
      .set(AGENTS.ACTIVE_CONFIGURATION_ID, configuration.id)
      .where(AGENTS.ID.eq(agentId))
      .execute()

    return configuration
  }

  fun testChat(userId: UUID, agentId: UUID, title: String = "Test Chat Title"): Chat {
    return dslContext
      .insertInto(CHATS)
      .set(CHATS.ID, UUID.randomUUID())
      .set(CHATS.USER_ID, userId)
      .set(CHATS.AGENT_ID, agentId)
      .set(CHATS.TITLE, title)
      .returning()
      .fetchOne(ChatsRecord::toChat)!!
  }

  fun testChatMessage(
    chatId: UUID,
    userId: UUID,
    content: String,
    senderType: String = "user",
    responseTo: UUID? = null,
    evaluation: Boolean? = null,
  ): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, chatId)
      .set(MESSAGES.SENDER, userId)
      .set(MESSAGES.SENDER_TYPE, "user")
      .set(MESSAGES.CONTENT, content)
      .set(MESSAGES.RESPONSE_TO, responseTo)
      .set(MESSAGES.EVALUATION, evaluation)
      .returning()
      .fetchOne(MessagesRecord::toMessage)!!
  }

  fun deleteTestChat(id: UUID) {
    dslContext.deleteFrom(CHATS).where(CHATS.ID.eq(id)).execute()
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

  fun insertTestCollection(name: String = "TestClass", size: Int = 1536) {
    val contentProperty =
      Property.builder()
        .name("content")
        .description("Document content")
        .dataType(listOf("text"))
        .build()

    val sizeProperty =
      Property.builder().name("size").description(size.toString()).dataType(listOf("text")).build()

    val newClass =
      WeaviateClass.builder()
        .className(name)
        .description("Test vector collection")
        .properties(listOf(contentProperty, sizeProperty))
        .build()

    client.schema().classCreator().withClass(newClass).run()
  }
}
