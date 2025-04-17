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
import java.util.*
import kotlin.random.Random
import kotlinx.coroutines.reactive.awaitSingle
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.barrage.llmao.app.adapters.whatsapp.model.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.model.toWhatsAppNumber
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentCollection
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.SettingKey
import net.barrage.llmao.core.model.SettingsUpdate
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.toAgent
import net.barrage.llmao.core.model.toAgentCollection
import net.barrage.llmao.core.model.toAgentConfiguration
import net.barrage.llmao.core.model.toChat
import net.barrage.llmao.core.model.toMessage
import net.barrage.llmao.core.model.toMessageGroup
import net.barrage.llmao.core.model.toMessageGroupEvaluation
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import net.barrage.llmao.tables.references.AGENT_PERMISSIONS
import net.barrage.llmao.tables.references.APPLICATION_SETTINGS
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.JIRA_API_KEYS
import net.barrage.llmao.tables.references.JIRA_WORKLOG_ATTRIBUTES
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.MESSAGE_GROUPS
import net.barrage.llmao.tables.references.MESSAGE_GROUP_EVALUATIONS
import net.barrage.llmao.tables.references.WHATS_APP_NUMBERS
import net.barrage.llmao.types.KUUID
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

    initConnectionPool()
    initDslContext()

    val dataSource =
      PGSimpleDataSource().apply {
        setURL(container.jdbcUrl)
        password = "test"
        user = "test"
        databaseName = "test"
      }

    val originalOut = System.out
    val originalErr = System.err

    var attempt = 0
    while (attempt < 5) {
      try {
        // Disable liquibase output
        System.setOut(PrintStream(FileOutputStream("/dev/null")))
        System.setErr(PrintStream(FileOutputStream("/dev/null")))
        val liquibase =
          Liquibase(
            "db/changelog.yaml",
            ClassLoaderResourceAccessor(),
            JdbcConnection(dataSource.connection),
          )
        liquibase.update()
        break
      } catch (e: Throwable) {
        System.setOut(originalOut)
        println("Postgres initialization error: ${e.message}")
        println("Attempting reconnection...")
        System.setOut(PrintStream(FileOutputStream("/dev/null")))
        attempt += 1
        sleep(500)
      }
    }

    System.setOut(originalOut)
    System.setErr(originalErr)
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

  suspend fun deleteTestAgent(id: UUID) {
    dslContext.deleteFrom(AGENTS).where(AGENTS.ID.eq(id)).awaitSingle()
  }

  suspend fun testAgent(
    name: String = "Test",
    active: Boolean = true,
    groups: List<String>? = null,
  ): Agent {
    val agent =
      dslContext
        .insertInto(AGENTS)
        .columns(AGENTS.NAME, AGENTS.DESCRIPTION, AGENTS.ACTIVE, AGENTS.ACTIVE, AGENTS.LANGUAGE)
        .values(name, "Test", active, active, "croatian")
        .returning()
        .awaitSingle()
        .toAgent()

    groups?.let {
      dslContext
        .insertInto(AGENT_PERMISSIONS)
        .columns(AGENT_PERMISSIONS.AGENT_ID, AGENT_PERMISSIONS.GROUP)
        .apply { groups.forEach { group -> values(agent.id, group) } }
        .awaitSingle()
    }

    return agent
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

  suspend fun testChat(
    user: User,
    agentId: UUID,
    title: String? = "Test Chat Title",
    type: String = "CHAT",
  ): Chat {
    return dslContext
      .insertInto(CHATS)
      .set(CHATS.ID, UUID.randomUUID())
      .set(CHATS.USER_ID, user.id)
      .set(CHATS.USERNAME, user.username)
      .set(CHATS.AGENT_ID, agentId)
      .set(CHATS.TITLE, title)
      .set(CHATS.TYPE, type)
      .returning()
      .awaitSingle()
      .toChat()
  }

  suspend fun deleteTestChat(id: UUID) {
    dslContext.deleteFrom(CHATS).where(CHATS.ID.eq(id)).awaitSingle()
  }

  suspend fun testMessagePair(
    chatId: UUID,
    agentConfigurationId: KUUID,
    userContent: String = "Test message",
    assistantContent: String = "Test response",
    evaluation: Boolean? = null,
    feedback: String? = null,
  ): MessageGroupAggregate {
    val messageGroup =
      dslContext
        .insertInto(MESSAGE_GROUPS)
        .set(MESSAGE_GROUPS.CHAT_ID, chatId)
        .set(MESSAGE_GROUPS.AGENT_CONFIGURATION_ID, agentConfigurationId)
        .returning()
        .awaitSingle()

    val userMessage =
      dslContext
        .insertInto(MESSAGES)
        .set(MESSAGES.MESSAGE_GROUP_ID, messageGroup.id)
        .set(MESSAGES.ORDER, 0)
        .set(MESSAGES.SENDER_TYPE, "user")
        .set(MESSAGES.CONTENT, userContent)
        .returning()
        .awaitSingle()
        .toMessage()

    val assistantMessage =
      dslContext
        .insertInto(MESSAGES)
        .set(MESSAGES.MESSAGE_GROUP_ID, messageGroup.id)
        .set(MESSAGES.ORDER, 1)
        .set(MESSAGES.SENDER_TYPE, "assistant")
        .set(MESSAGES.CONTENT, assistantContent)
        .set(MESSAGES.FINISH_REASON, FinishReason.Stop.value)
        .returning()
        .awaitSingle()
        .toMessage()

    val evaluated =
      evaluation?.let {
        dslContext
          .insertInto(MESSAGE_GROUP_EVALUATIONS)
          .set(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID, messageGroup.id)
          .set(MESSAGE_GROUP_EVALUATIONS.EVALUATION, evaluation)
          .set(MESSAGE_GROUP_EVALUATIONS.FEEDBACK, feedback)
          .returning()
          .awaitSingle()
      }

    return MessageGroupAggregate(
      group = messageGroup.toMessageGroup(),
      messages = mutableListOf(userMessage, assistantMessage),
      evaluation = evaluated?.toMessageGroupEvaluation(),
    )
  }

  suspend fun testWhatsAppNumber(user: User, phoneNumber: String): WhatsAppNumber {
    return dslContext
      .insertInto(WHATS_APP_NUMBERS)
      .set(WHATS_APP_NUMBERS.USER_ID, user.id)
      .set(WHATS_APP_NUMBERS.USERNAME, user.username)
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

  suspend fun testJiraApiKey(userId: String, apiKey: String) {
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
    settings.removals?.forEach { key ->
      dslContext
        .deleteFrom(APPLICATION_SETTINGS)
        .where(APPLICATION_SETTINGS.NAME.eq(key.name))
        .awaitSingle()
    }

    settings.updates?.let { updates ->
      dslContext
        .insertInto(APPLICATION_SETTINGS, APPLICATION_SETTINGS.NAME, APPLICATION_SETTINGS.VALUE)
        .apply { updates.forEach { setting -> values(setting.key.name, setting.value) } }
        .onConflict(APPLICATION_SETTINGS.NAME)
        .doUpdate()
        .set(APPLICATION_SETTINGS.VALUE, excluded(APPLICATION_SETTINGS.VALUE))
        .awaitSingle()
    }
  }
}

internal val LOG = io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.TestContainers")

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
    groups: List<String>? = null,
  ) {

    val properties =
      mutableListOf<Property>(
        Property.builder().name("collection_id").dataType(listOf("uuid")).build(),
        Property.builder().name("size").dataType(listOf("int")).build(),
        Property.builder().name("name").description(name).dataType(listOf("text")).build(),
        Property.builder().name("embedding_provider").dataType(listOf("text")).build(),
        Property.builder().name("embedding_model").dataType(listOf("text")).build(),
        Property.builder().name("groups").dataType(listOf("text[]")).build(),
      )

    val newClass =
      WeaviateClass.builder()
        .className(name)
        .description("Test vector collection")
        .properties(properties)
        .build()

    val idVector: List<Float> = List(size) { Random.nextFloat() }
    client.schema().classCreator().withClass(newClass).run()
    val result =
      client
        .data()
        .creator()
        .withClassName(name)
        .withVector(idVector.toTypedArray())
        .withID(UUID(0L, 0L).toString())
        .withProperties(
          mapOf(
            "name" to name,
            "collection_id" to KUUID.randomUUID(),
            "size" to size,
            "embedding_provider" to embeddingProvider,
            "embedding_model" to embeddingModel,
            "groups" to groups,
          )
        )
        .run()

    assert(result.error == null)

    LOG.info("inserted test collection: {}", name)
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
      .withWhere(
        WhereFilter.builder()
          .operator(Operator.NotEqual)
          .path("id")
          .valueString(UUID(0L, 0L).toString())
          .build()
      )
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
