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
import net.barrage.llmao.app.adapters.whatsapp.dto.WhatsAppAgentDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.toWhatsAppAgentDTO
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppNumber
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
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentCollectionsRecord
import net.barrage.llmao.tables.records.AgentConfigurationsRecord
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.records.SessionsRecord
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.records.WhatsAppAgentsRecord
import net.barrage.llmao.tables.records.WhatsAppChatsRecord
import net.barrage.llmao.tables.records.WhatsAppMessagesRecord
import net.barrage.llmao.tables.records.WhatsAppNumbersRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.SESSIONS
import net.barrage.llmao.tables.references.USERS
import net.barrage.llmao.tables.references.WHATS_APP_AGENTS
import net.barrage.llmao.tables.references.WHATS_APP_CHATS
import net.barrage.llmao.tables.references.WHATS_APP_MESSAGES
import net.barrage.llmao.tables.references.WHATS_APP_NUMBERS
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

  fun testAgent(name: String = "Test", active: Boolean = true): Agent {
    val agent =
      dslContext
        .insertInto(AGENTS)
        .columns(AGENTS.NAME, AGENTS.DESCRIPTION, AGENTS.ACTIVE, AGENTS.ACTIVE, AGENTS.LANGUAGE)
        .values(name, "Test", active, active, "croatian")
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
    embeddingProvider: String = "azure",
    embeddingModel: String = "text-embedding-ada-002",
    vectorProvider: String,
  ): AgentCollection {
    return dslContext
      .insertInto(AGENT_COLLECTIONS)
      .set(AGENT_COLLECTIONS.AGENT_ID, agentId)
      .set(AGENT_COLLECTIONS.COLLECTION, collection)
      .set(AGENT_COLLECTIONS.AMOUNT, amount)
      .set(AGENT_COLLECTIONS.INSTRUCTION, instruction)
      .set(AGENT_COLLECTIONS.EMBEDDING_PROVIDER, embeddingProvider)
      .set(AGENT_COLLECTIONS.EMBEDDING_MODEL, embeddingModel)
      .set(AGENT_COLLECTIONS.VECTOR_PROVIDER, vectorProvider)
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

  fun deleteTestChat(id: UUID) {
    dslContext.deleteFrom(CHATS).where(CHATS.ID.eq(id)).execute()
  }

  fun testChatMessage(
    chatId: UUID,
    userId: UUID,
    content: String = "Test message",
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

  fun testWhatsAppNumber(userId: UUID, phoneNumber: String): WhatsAppNumber {
    return dslContext
      .insertInto(WHATS_APP_NUMBERS)
      .set(WHATS_APP_NUMBERS.USER_ID, userId)
      .set(WHATS_APP_NUMBERS.PHONE_NUMBER, phoneNumber)
      .returning()
      .fetchOne(WhatsAppNumbersRecord::toWhatsAppNumber)!!
  }

  fun deleteTestWhatsAppNumber(id: UUID) {
    dslContext.deleteFrom(WHATS_APP_NUMBERS).where(WHATS_APP_NUMBERS.ID.eq(id)).execute()
  }

  fun testWhatsAppAgent(
    name: String = "Test WhatsApp Agent",
    active: Boolean = true,
  ): WhatsAppAgentDTO {
    val agent =
      dslContext
        .insertInto(WHATS_APP_AGENTS)
        .columns(
          WHATS_APP_AGENTS.NAME,
          WHATS_APP_AGENTS.DESCRIPTION,
          WHATS_APP_AGENTS.CONTEXT,
          WHATS_APP_AGENTS.LLM_PROVIDER,
          WHATS_APP_AGENTS.MODEL,
          WHATS_APP_AGENTS.TEMPERATURE,
          WHATS_APP_AGENTS.LANGUAGE,
          WHATS_APP_AGENTS.ACTIVE,
        )
        .values(
          name,
          "Test Description",
          "WhatsApp Test Agent Context",
          "openai",
          "gpt-4",
          0.4,
          "croatian",
          active,
        )
        .returning()
        .fetchOne(WhatsAppAgentsRecord::toWhatsAppAgentDTO)!!

    return agent
  }

  fun deleteTestWhatsAppAgent(id: UUID) {
    dslContext.deleteFrom(WHATS_APP_AGENTS).where(WHATS_APP_AGENTS.ID.eq(id)).execute()
  }

  fun testWhatsAppChat(userId: UUID): WhatsAppChat {
    return dslContext
      .insertInto(WHATS_APP_CHATS)
      .set(WHATS_APP_CHATS.ID, UUID.randomUUID())
      .set(WHATS_APP_CHATS.USER_ID, userId)
      .returning()
      .fetchOne(WhatsAppChatsRecord::toWhatsAppChat)!!
  }

  fun deleteTestWhatsAppChat(id: UUID) {
    dslContext.deleteFrom(WHATS_APP_CHATS).where(WHATS_APP_CHATS.ID.eq(id)).execute()
  }

  fun testWhatsAppMessage(
    chatId: UUID,
    userId: UUID,
    content: String = "Test message",
    senderType: String = "user",
    responseTo: UUID? = null,
  ): WhatsAppMessage {
    return dslContext
      .insertInto(WHATS_APP_MESSAGES)
      .set(WHATS_APP_MESSAGES.CHAT_ID, chatId)
      .set(WHATS_APP_MESSAGES.SENDER, userId)
      .set(WHATS_APP_MESSAGES.SENDER_TYPE, senderType)
      .set(WHATS_APP_MESSAGES.CONTENT, content)
      .set(WHATS_APP_MESSAGES.RESPONSE_TO, responseTo)
      .returning()
      .fetchOne(WhatsAppMessagesRecord::toWhatsAppMessage)!!
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

  fun insertTestCollection(
    name: String = "TestClass",
    size: Int = 1536,
    embeddingProvider: String = "azure",
    embeddingModel: String = "text-embedding-ada-002",
  ) {

    val idProperty =
      Property.builder()
        .name("collection_id")
        .description(KUUID.randomUUID().toString())
        .dataType(listOf("text"))
        .build()

    val sizeProperty =
      Property.builder().name("size").description(size.toString()).dataType(listOf("text")).build()

    val nameProperty =
      Property.builder().name("name").description(name).dataType(listOf("text")).build()

    val embeddingProviderProperty =
      Property.builder()
        .name("embedding_provider")
        .description(embeddingProvider)
        .dataType(listOf("text"))
        .build()

    val embeddingModelProperty =
      Property.builder()
        .name("embedding_model")
        .description(embeddingModel)
        .dataType(listOf("text"))
        .build()

    val newClass =
      WeaviateClass.builder()
        .className(name)
        .description("Test vector collection")
        .properties(
          listOf(
            idProperty,
            sizeProperty,
            nameProperty,
            embeddingProviderProperty,
            embeddingModelProperty,
          )
        )
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
