package net.barrage.llmao.plugins

import io.ktor.server.application.*
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource

fun initDatabase(env: ApplicationEnvironment): DSLContext {
  val url = env.config.property("db.url").getString()
  val user = env.config.property("db.user").getString()
  val pw = env.config.property("db.password").getString()
  val dataSource =
    PGSimpleDataSource().apply {
      setURL(url)
      this.user = user
      this.password = pw
    }
  val dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)

  if (env.config.property("db.runMigrations").getString().toBoolean()) {
    runLiquibaseMigration(env)
  }

  return dslContext
}

fun runLiquibaseMigration(env: ApplicationEnvironment) {
  val url = env.config.property("db.url").getString()
  val user = env.config.property("db.user").getString()
  val pw = env.config.property("db.password").getString()
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
