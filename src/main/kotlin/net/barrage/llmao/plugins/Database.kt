package net.barrage.llmao.plugins

import io.ktor.server.application.*
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource

object Database {
  lateinit var dslContext: DSLContext

  fun init(environment: ApplicationEnvironment) {
    // Set up the data source
    val dataSource = PGSimpleDataSource().apply {
      setURL(environment.config.property("db.url").getString())
      user = environment.config.property("db.user").getString()
      password = environment.config.property("db.password").getString()
    }

    // Initialize the DSLContext
    dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)
  }
}

fun <T> transaction(block: (DSLContext) -> T): T {
  return Database.dslContext.transactionResult { configuration ->
    val dsl = DSL.using(configuration)
    block(dsl)
  }
}