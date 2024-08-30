package net.barrage.llmao

import net.barrage.llmao.plugins.*
import io.ktor.server.application.*
import net.barrage.llmao.tables.Agents
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    Database.init(environment)
    configureSerialization()
    configureRouting()
    configureRequestValidation()
}
