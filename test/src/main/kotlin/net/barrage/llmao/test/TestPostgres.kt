package net.barrage.llmao.test

import io.ktor.server.config.ApplicationConfig
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import java.lang.Thread.sleep
import java.time.Duration
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.Plugins
import net.barrage.llmao.core.database.runCoreMigrations
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.core.tables.references.APPLICATION_SETTINGS
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DefaultConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

class TestPostgres() {
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
  }

  fun migrate(config: ApplicationConfig) {
    runCoreMigrations(config)
    Plugins.migrate(config)
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
    val configuration = DefaultConfiguration().set(connectionPool).set(SQLDialect.POSTGRES)
    dslContext = configuration.dsl()
    sleep(1000)
  }

  suspend fun testSettings(settings: SettingsUpdate) {
    settings.removals?.forEach { key ->
      dslContext
        .deleteFrom(APPLICATION_SETTINGS)
        .where(APPLICATION_SETTINGS.NAME.eq(key))
        .awaitSingle()
    }

    settings.updates?.let { updates ->
      dslContext
        .insertInto(APPLICATION_SETTINGS, APPLICATION_SETTINGS.NAME, APPLICATION_SETTINGS.VALUE)
        .apply { updates.forEach { setting -> values(setting.key, setting.value) } }
        .onConflict(APPLICATION_SETTINGS.NAME)
        .doUpdate()
        .set(APPLICATION_SETTINGS.VALUE, excluded(APPLICATION_SETTINGS.VALUE))
        .awaitSingle()
    }
  }
}
