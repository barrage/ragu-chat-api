package net.barrage.llmao

import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.schema.model.Property
import io.weaviate.client.v1.schema.model.WeaviateClass
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.Thread.sleep
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import kotlinx.coroutines.reactive.awaitSingle
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
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
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import net.barrage.llmao.tables.references.APPLICATION_SETTINGS
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.JIRA_API_KEYS
import net.barrage.llmao.tables.references.JIRA_WORKLOG_ATTRIBUTES
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.MESSAGE_EVALUATIONS
import net.barrage.llmao.tables.references.SESSIONS
import net.barrage.llmao.tables.references.USERS
import net.barrage.llmao.tables.references.WHATS_APP_CHATS
import net.barrage.llmao.tables.references.WHATS_APP_MESSAGES
import net.barrage.llmao.tables.references.WHATS_APP_NUMBERS
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DefaultConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.MinIOContainer
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
        withCommand("-c max_connections=500")
      }
      .waitingFor(Wait.defaultWaitStrategy())

  lateinit var dslContext: DSLContext
  private lateinit var connectionPool: ConnectionPool

  init {
    container.start()

    // Fixes all errors related to container not being ready yet
    sleep(150)

    initConnectionPool()
    initDslContext()

    val dataSource =
      PGSimpleDataSource().apply {
        setURL(container.jdbcUrl)
        password = "test"
        user = "test"
        databaseName = "test"
      }

    // The insane lengths one has to go through to make this
    // hot garbage piece of shit library shut the fuck up is unfathomable
    val originalOut = System.out
    val originalErr = System.err
    try {
      System.setOut(PrintStream(FileOutputStream("/dev/null")))
      System.setErr(PrintStream(FileOutputStream("/dev/null")))
      val liquibase =
        Liquibase(
          "db/changelog.yaml",
          ClassLoaderResourceAccessor(),
          JdbcConnection(dataSource.connection),
        )
      liquibase.update()
    } finally {
      System.setOut(originalOut)
      System.setErr(originalErr)
    }
  }

  private fun initConnectionPool() {
    val connectionFactory =
      ConnectionFactories.get(
        ConnectionFactoryOptions.builder()
          .option(ConnectionFactoryOptions.DRIVER, "postgresql")
          .option(ConnectionFactoryOptions.HOST, container.host)
          .option(ConnectionFactoryOptions.PORT, container.getMappedPort(5432))
          .option(ConnectionFactoryOptions.DATABASE, container.databaseName)
          .option(ConnectionFactoryOptions.USER, container.username)
          .option(ConnectionFactoryOptions.PASSWORD, container.password)
          .build()
      )

    val poolConfiguration =
      ConnectionPoolConfiguration.builder(connectionFactory)
        .maxIdleTime(Duration.ofMillis(1000))
        .maxSize(10)
        .build()

    connectionPool = ConnectionPool(poolConfiguration)
  }

  private fun initDslContext() {
    val configuration = DefaultConfiguration().set(connectionPool).set(SQLDialect.POSTGRES)
    dslContext = DSL.using(configuration)
  }

  @BeforeEach
  fun resetConnectionPool() {
    connectionPool.dispose()
    initConnectionPool()
    initDslContext()
  }

  @AfterEach
  fun closeConnectionPool() {
    connectionPool.dispose()
  }

  suspend fun testUser(
    email: String = "test@user.me",
    admin: Boolean,
    active: Boolean = true,
    fullName: String = "Test User",
    firstName: String = "Test",
    lastName: String = "User",
    deletedAt: OffsetDateTime? = null,
  ): User {
    return dslContext
      .insertInto(USERS)
      .columns(
        USERS.EMAIL,
        USERS.FULL_NAME,
        USERS.FIRST_NAME,
        USERS.LAST_NAME,
        USERS.ROLE,
        USERS.ACTIVE,
        USERS.DELETED_AT,
      )
      .values(
        email,
        fullName,
        firstName,
        lastName,
        if (admin) "ADMIN" else "USER",
        active,
        deletedAt,
      )
      .returning()
      .awaitSingle()
      .toUser()
  }

  suspend fun deleteTestAgent(id: UUID) {
    dslContext.deleteFrom(AGENTS).where(AGENTS.ID.eq(id)).awaitSingle()
  }

  suspend fun deleteTestUser(id: UUID) {
    dslContext.deleteFrom(USERS).where(USERS.ID.eq(id)).awaitSingle()
  }

  suspend fun testSession(userId: UUID): Session {
    return dslContext
      .insertInto(SESSIONS)
      .columns(SESSIONS.USER_ID, SESSIONS.EXPIRES_AT)
      .values(userId, OffsetDateTime.now().plusDays(1))
      .returning()
      .awaitSingle()
      .toSessionData()
  }

  suspend fun testAgent(name: String = "Test", active: Boolean = true): Agent {
    return dslContext
      .insertInto(AGENTS)
      .columns(AGENTS.NAME, AGENTS.DESCRIPTION, AGENTS.ACTIVE, AGENTS.ACTIVE, AGENTS.LANGUAGE)
      .values(name, "Test", active, active, "croatian")
      .returning()
      .awaitSingle()
      .toAgent()
  }

  suspend fun testAgentConfiguration(
    agentId: UUID,
    version: Int = 1,
    context: String = "Test",
    llmProvider: String = "openai",
    model: String = "gpt-4",
    temperature: Double = 0.1,
    presencePenalty: Double? = null,
    maxCompletionTokens: Int? = null,
    titleInstruction: String? = null,
    summaryInstruction: String? = null,
    errorMessage: String? = null,
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
          AGENT_CONFIGURATIONS.TEMPERATURE,
          AGENT_CONFIGURATIONS.PRESENCE_PENALTY,
          AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS,
          AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
          AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
          AGENT_CONFIGURATIONS.ERROR_MESSAGE,
        )
        .values(
          agentId,
          version,
          context,
          llmProvider,
          model,
          temperature,
          presencePenalty,
          maxCompletionTokens,
          titleInstruction,
          summaryInstruction,
          errorMessage,
        )
        .returning()
        .awaitSingle()
        .toAgentConfiguration()

    dslContext
      .update(AGENTS)
      .set(AGENTS.ACTIVE_CONFIGURATION_ID, configuration.id)
      .where(AGENTS.ID.eq(agentId))
      .awaitSingle()

    return configuration
  }

  suspend fun testAgentCollection(
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
      .awaitSingle()
      .toAgentCollection()
  }

  suspend fun testChat(userId: UUID, agentId: UUID, title: String? = "Test Chat Title"): Chat {
    return dslContext
      .insertInto(CHATS)
      .set(CHATS.ID, UUID.randomUUID())
      .set(CHATS.USER_ID, userId)
      .set(CHATS.AGENT_ID, agentId)
      .set(CHATS.TITLE, title)
      .returning()
      .awaitSingle()
      .toChat()
  }

  suspend fun testChatMessage(
    chatId: UUID,
    userId: UUID,
    content: String = "Test message",
    senderType: String = "user",
    responseTo: UUID? = null,
    evaluation: Boolean? = null,
    feedback: String? = null,
  ): Message {
    val message =
      dslContext
        .insertInto(MESSAGES)
        .set(MESSAGES.CHAT_ID, chatId)
        .set(MESSAGES.SENDER, userId)
        .set(MESSAGES.SENDER_TYPE, senderType)
        .set(MESSAGES.CONTENT, content)
        .set(MESSAGES.RESPONSE_TO, responseTo)
        .returning()
        .awaitSingle()
        .toMessage()

    if (evaluation != null) {
      dslContext
        .insertInto(MESSAGE_EVALUATIONS)
        .set(MESSAGE_EVALUATIONS.MESSAGE_ID, message.id)
        .set(MESSAGE_EVALUATIONS.EVALUATION, evaluation)
        .set(MESSAGE_EVALUATIONS.FEEDBACK, feedback)
        .awaitSingle()
    }

    return message
  }

  suspend fun testWhatsAppNumber(userId: UUID, phoneNumber: String): WhatsAppNumber {
    return dslContext
      .insertInto(WHATS_APP_NUMBERS)
      .set(WHATS_APP_NUMBERS.USER_ID, userId)
      .set(WHATS_APP_NUMBERS.PHONE_NUMBER, phoneNumber)
      .returning()
      .awaitSingle()
      .toWhatsAppNumber()
  }

  suspend fun deleteTestWhatsAppNumber(id: UUID) {
    dslContext.deleteFrom(WHATS_APP_NUMBERS).where(WHATS_APP_NUMBERS.ID.eq(id)).awaitSingle()
  }

  suspend fun setWhatsAppAgent(agentId: KUUID) {
    dslContext
      .insertInto(APPLICATION_SETTINGS)
      .set(APPLICATION_SETTINGS.NAME, SettingKey.WHATSAPP_AGENT_ID.name)
      .set(APPLICATION_SETTINGS.VALUE, agentId.toString())
      .onConflict(APPLICATION_SETTINGS.NAME)
      .doUpdate()
      .set(APPLICATION_SETTINGS.VALUE, agentId.toString())
      .awaitSingle()
  }

  suspend fun deleteWhatsAppAgent() {
    dslContext
      .deleteFrom(APPLICATION_SETTINGS)
      .where(APPLICATION_SETTINGS.NAME.eq(SettingKey.WHATSAPP_AGENT_ID.name))
      .awaitSingle()
  }

  suspend fun testWhatsAppChat(userId: UUID): WhatsAppChat {
    return dslContext
      .insertInto(WHATS_APP_CHATS)
      .set(WHATS_APP_CHATS.ID, UUID.randomUUID())
      .set(WHATS_APP_CHATS.USER_ID, userId)
      .returning()
      .awaitSingle()
      .toWhatsAppChat()
  }

  suspend fun deleteTestWhatsAppChat(id: UUID) {
    dslContext.deleteFrom(WHATS_APP_CHATS).where(WHATS_APP_CHATS.ID.eq(id)).awaitSingle()
  }

  suspend fun testWhatsAppMessage(
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
      .awaitSingle()
      .toWhatsAppMessage()
  }

  suspend fun testJiraApiKey(userId: UUID, apiKey: String) {
    dslContext
      .insertInto(JIRA_API_KEYS)
      .set(JIRA_API_KEYS.USER_ID, userId)
      .set(JIRA_API_KEYS.API_KEY, apiKey)
      .awaitSingle()
  }

  suspend fun testJiraWorklogAttribute(id: String, description: String, required: Boolean) {
    dslContext
      .insertInto(JIRA_WORKLOG_ATTRIBUTES)
      .set(JIRA_WORKLOG_ATTRIBUTES.ID, id)
      .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
      .set(JIRA_WORKLOG_ATTRIBUTES.REQUIRED, required)
      .awaitSingle()
  }

  suspend fun testSettings(settings: SettingsUpdate) {
    dslContext
      .insertInto(APPLICATION_SETTINGS, APPLICATION_SETTINGS.NAME, APPLICATION_SETTINGS.VALUE)
      .apply { settings.updates.forEach { setting -> values(setting.key.name, setting.value) } }
      .onConflict(APPLICATION_SETTINGS.NAME)
      .doUpdate()
      .set(APPLICATION_SETTINGS.VALUE, excluded(APPLICATION_SETTINGS.VALUE))
      .awaitSingle()
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

class TestMinio {
  val container: MinIOContainer =
    MinIOContainer("minio/minio:latest")
      .withUserName("testMinio")
      .withPassword("testMinio")
      .waitingFor(Wait.defaultWaitStrategy())

  val client: MinioClient

  init {
    container.start()

    client =
      MinioClient.builder().endpoint(container.s3URL).credentials("testMinio", "testMinio").build()

    client.makeBucket(MakeBucketArgs.builder().bucket("test").build())
  }
}
