package net.barrage.llmao.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.flywaydb.core.Flyway
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
  runFlyWayMigrations(dataSource)
  return dslContext
}

private fun runFlyWayMigrations(dataSource: PGSimpleDataSource) {
  val flyway = Flyway.configure().dataSource(dataSource).validateMigrationNaming(true).load()
  flyway.repair()
  flyway.migrate()
}
