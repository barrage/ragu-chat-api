package net.barrage.llmao.plugins

import io.ktor.server.config.*
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT
import io.r2dbc.spi.ConnectionFactoryOptions.USER
import java.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.string
import net.barrage.llmao.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.postgresql.ds.PGSimpleDataSource

fun initDatabase(config: ApplicationConfig, applicationStopping: Job): DSLContext {
  val r2dbcHost = config.property("db.r2dbcHost").getString()
  val r2dbcPort = config.property("db.r2dbcPort").getString()
  val r2dbcDatabase = config.property("db.r2dbcDatabase").getString()
  val user = config.property("db.user").getString()
  val pw = config.property("db.password").getString()

  val connectionFactory =
    ConnectionFactories.get(
      ConnectionFactoryOptions.builder()
        .option(DRIVER, "postgresql")
        .option(HOST, r2dbcHost)
        .option(PORT, r2dbcPort.toInt())
        .option(USER, user)
        .option(PASSWORD, pw)
        .option(DATABASE, r2dbcDatabase)
        .build()
    )

  val poolConfiguration: ConnectionPoolConfiguration =
    ConnectionPoolConfiguration.builder(connectionFactory)
      .maxIdleTime(Duration.ofMillis(1000))
      .maxSize(10)
      .build()

  val pool = ConnectionPool(poolConfiguration)

  // Monitor shutdown to close the connection pool
  applicationStopping.invokeOnCompletion { pool.dispose() }

  val configuration = DefaultConfiguration().set(pool).set(SQLDialect.POSTGRES)

  val dslContext = DSL.using(configuration)

  if (config.property("db.runMigrations").getString().toBoolean()) {
    runLiquibaseMigration(config)
  }

  if (config.string("admin.email").isNotBlank() && config.string("admin.fullName").isNotBlank()) {
    insertAdminUser(dslContext, config)
  }

  return dslContext
}

fun runLiquibaseMigration(config: ApplicationConfig) {
  val url = config.property("db.url").getString()
  val user = config.property("db.user").getString()
  val pw = config.property("db.password").getString()
  val changeLogFile = "db/changelog.yaml"
  val dataSource =
    PGSimpleDataSource().apply {
      setURL(url)
      this.user = user
      this.password = pw
    }

  dataSource.connection.use { connection ->
    val liquibase =
      Liquibase(changeLogFile, ClassLoaderResourceAccessor(), JdbcConnection(connection))
    liquibase.update("main")
  }
}

fun insertAdminUser(dslContext: DSLContext, config: ApplicationConfig) {
  val email = config.string("admin.email")
  val fullName = config.string("admin.fullName")
  val firstName = config.string("admin.firstName")
  val lastName = config.string("admin.lastName")
  val role = Role.ADMIN.name

  runBlocking {
    dslContext
      .insertInto(USERS)
      .columns(
        USERS.EMAIL,
        USERS.FULL_NAME,
        USERS.FIRST_NAME,
        USERS.LAST_NAME,
        USERS.ROLE,
        USERS.ACTIVE,
      )
      .values(email, fullName, firstName, lastName, role, true)
      .onConflict(USERS.EMAIL)
      .doNothing()
      .awaitSingle()
  }
}
