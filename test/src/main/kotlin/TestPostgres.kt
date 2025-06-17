package net.barrage.llmao.test

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.Thread.sleep
import java.time.Duration
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DefaultConfiguration
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

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

    // Set Liquibase system property before initialization
    System.setProperty("liquibase.duplicateFileMode", "WARN")

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
            .apply {
              // Set property to handle duplicate files
              setChangeLogParameter("liquibase.duplicateFileMode", "WARN")
            }
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
    dslContext = configuration.dsl()
  }

  fun resetConnectionPool() {
    connectionPool.dispose()
    initConnectionPool()
    initDslContext()
  }

  fun closeConnectionPool() {
    connectionPool.dispose()
  }

  //  suspend fun testJiraApiKey(userId: String, apiKey: String) {
  //    dslContext
  //      .insertInto(JIRA_API_KEYS)
  //      .set(JIRA_API_KEYS.USER_ID, userId)
  //      .set(JIRA_API_KEYS.API_KEY, apiKey)
  //      .awaitSingle()
  //  }
  //
  //  suspend fun testJiraWorklogAttribute(id: String, description: String, required: Boolean) {
  //    dslContext
  //      .insertInto(JIRA_WORKLOG_ATTRIBUTES)
  //      .set(JIRA_WORKLOG_ATTRIBUTES.ID, id)
  //      .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
  //      .set(JIRA_WORKLOG_ATTRIBUTES.REQUIRED, required)
  //      .awaitSingle()
  //  }
}
