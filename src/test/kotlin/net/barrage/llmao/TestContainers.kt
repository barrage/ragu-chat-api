package net.barrage.llmao

import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.schema.model.Property
import io.weaviate.client.v1.schema.model.WeaviateClass
import java.time.OffsetDateTime
import java.util.*
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toAgentCollection
import net.barrage.llmao.core.models.toAgentConfiguration
import net.barrage.llmao.core.models.toChat
import net.barrage.llmao.core.models.toMessage
import net.barrage.llmao.core.models.toSessionData
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.tables.records.AgentCollectionsRecord
import net.barrage.llmao.tables.records.AgentConfigurationsRecord
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.records.SessionsRecord
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
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
import org.testcontainers.utility.MountableFile
import org.testcontainers.weaviate.WeaviateContainer
import org.wiremock.integrations.testcontainers.WireMockContainer

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

  private val liquibase: Liquibase

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

  fun testAgentCollection(
    agentId: UUID,
    collection: String,
    amount: Int,
    instruction: String,
  ): AgentCollection {
    return dslContext
      .insertInto(AGENT_COLLECTIONS)
      .set(AGENT_COLLECTIONS.AGENT_ID, agentId)
      .set(AGENT_COLLECTIONS.COLLECTION, collection)
      .set(AGENT_COLLECTIONS.AMOUNT, amount)
      .set(AGENT_COLLECTIONS.INSTRUCTION, instruction)
      .returning()
      .fetchInto(AgentCollectionsRecord::class.java)
      .map { it.toAgentCollection() }
      .first()
  }

  fun testChat(userId: UUID, agentId: UUID, title: String? = "Test Chat Title"): Chat {
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
      .set(MESSAGES.SENDER_TYPE, senderType)
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

  fun insertVectors(collection: String, vectors: List<Pair<String, List<Float>>>) {
    val batcher = client.batch().objectsBatcher()

    vectors.forEach { (content, vector) ->
      val properties = HashMap<String, Any>()
      properties["content"] = content

      val obj =
        WeaviateObject.builder()
          .className(collection)
          .properties(properties)
          .vector(vector.toTypedArray())
          .build()

      batcher.withObject(obj)
    }

    val result = batcher.run()

    if (result.hasErrors()) {
      throw RuntimeException("Error inserting vectors: ${result.error}")
    }
  }

  fun deleteVectors(collection: String) {
    client
      .batch()
      .objectsBatchDeleter()
      .withClassName(collection)
      .withWhere(WhereFilter.builder().operator(Operator.Like).path("id").valueString("*").build())
      .run()
  }
}

/**
 * Holds the Wiremock test container.
 *
 * The `mappings` directory contains the definition for responses we mock.
 *
 * Each response will have a `bodyFileName` in the response designating the body for it. When the
 * container is started it will copy all files from the `responses` directory to the
 * `/home/wiremock/__files` directory in the container and the directory will be flat, containing
 * only files. The `bodyFileName` must be equal to the file name in the container directory, which
 * is also why every response must have a unique name, regardless of the directory.
 */
class Wiremock {
  val container: WireMockContainer =
    WireMockContainer("wiremock/wiremock:3.9.2")
      .map("openai/v1_chat_completions_completion")
      .map("openai/v1_chat_completions_stream")
      .map("openai/v1_chat_completions_title")
      .map("openai/v1_chat_completions_whitespace_stream")
      .map("openai/v1_chat_completions_long_stream")
      .map("openai/v1_embeddings_ada-002")
      .map("openai/v1_embeddings_large-3")
      .response("openai", "openai_v1_chat_completions_completion_response.json")
      .response("openai", "openai_v1_chat_completions_stream_response.txt")
      .response("openai", "openai_v1_chat_completions_long_stream_response.txt")
      .response("openai", "openai_v1_chat_completions_title_response.json")
      .response("openai", "openai_v1_chat_completions_whitespace_stream_response.txt")
      .response("openai", "openai_v1_embeddings_ada-002_response.json")
      .response("openai", "openai_v1_embeddings_large-3_response.json")
      .map("azure/embeddings_ada-002")
      .response("azure", "azure_embeddings_ada-002_response.json")
      .map("fembed/fembed_list_models")
      .response("fembed", "fembed_list_models_response.json")

  init {
    container.start()
  }
}

private fun WireMockContainer.map(name: String): WireMockContainer {
  return withMappingFromResource(name, "wiremock/mappings/$name.json")
}

private fun WireMockContainer.response(dir: String, name: String): WireMockContainer {
  return withCopyFileToContainer(
    MountableFile.forClasspathResource("wiremock/responses/$dir/$name"),
    "/home/wiremock/__files/$name",
  )
}
