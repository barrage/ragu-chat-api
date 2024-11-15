package net.barrage.llmao.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource

fun initDatabase(config: ApplicationConfig): DSLContext {
  val url = config.property("db.url").getString()
  val user = config.property("db.user").getString()
  val pw = config.property("db.password").getString()
  val dataSource =
    PGSimpleDataSource().apply {
      setURL(url)
      this.user = user
      this.password = pw
    }
  val dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)

  if (config.property("db.runMigrations").getString().toBoolean()) {
    runLiquibaseMigration(config)
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
