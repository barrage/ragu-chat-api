package net.barrage.llmao.core.database

import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

interface Atomic {
    val dslContext: DSLContext

    suspend fun <T, R : Atomic> transaction(
        factory: (DSLContext) -> R,
        block: suspend (R) -> T
    ): T =
        dslContext.transactionCoroutine { block(factory(it.dsl())) }
}
