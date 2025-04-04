package net.barrage.llmao.core

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
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.barrage.llmao.core.model.common.PropertyUpdate
import org.jooq.DSLContext
import org.jooq.InsertOnDuplicateSetMoreStep
import org.jooq.InsertSetMoreStep
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.TableField
import org.jooq.UpdateSetMoreStep
import org.jooq.impl.DSL
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DefaultConfiguration
import org.postgresql.ds.PGSimpleDataSource

/**
 * The only exception to the dependency inversion principle. Repositories depend directly on
 * [DSLContext] since there is almost no reason to abstract it.
 */
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

  return dslContext
}

private fun runLiquibaseMigration(config: ApplicationConfig) {
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

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Semantics are defined in [PropertyUpdate].
 */
fun <R : Record, T> UpdateSetMoreStep<R>.set(
  update: PropertyUpdate<T>,
  field: TableField<R, T>,
): UpdateSetMoreStep<R> {
  return when (update) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> this

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, update.value)

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

fun <R : Record, T> InsertOnDuplicateSetMoreStep<R>.set(
  update: PropertyUpdate<T>,
  field: TableField<R, T>,
): InsertOnDuplicateSetMoreStep<R> {
  return when (update) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> set(field, excluded(field))

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, update.value)

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

fun <R : Record, T> InsertSetMoreStep<R>.set(
  value: PropertyUpdate<T>,
  field: TableField<R, T>,
  defaultIfUndefined: T? = null,
): InsertSetMoreStep<R> {
  return when (value) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> defaultIfUndefined?.let { set(field, it) } ?: this

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, value.value)

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Implementation for *required* properties.
 *
 * If the value is null, leaves the statement as is.
 */
fun <R : Record, T> UpdateSetMoreStep<R>.set(
  update: T?,
  field: TableField<R, T>,
  defaultIfNull: T? = null,
): UpdateSetMoreStep<R> =
  update?.let { set(field, it) } ?: defaultIfNull?.let { set(field, it) } ?: this

/**
 * Implementation for *required* properties.
 *
 * If the value is null, tries to set the default value. If the default value is null, leaves the
 * statement as is.
 */
fun <R : Record, T> InsertSetMoreStep<R>.set(
  value: T?,
  field: TableField<R, T>,
  defaultIfNull: T? = null,
): InsertSetMoreStep<R> =
  value?.let { set(field, it) } ?: defaultIfNull?.let { set(field, it) } ?: this

/**
 * Implementation for *required* properties.
 *
 * If the value is null, tries to set the default value. If the default value is null, leaves the
 * value as is.
 */
fun <R : Record, T> InsertOnDuplicateSetMoreStep<R>.set(
  value: T?,
  field: TableField<R, T>,
  defaultIfNull: T? = null,
): InsertOnDuplicateSetMoreStep<R> =
  value?.let { set(field, it) }
    ?: defaultIfNull?.let { set(field, it) }
    ?: set(field, excluded(field))
