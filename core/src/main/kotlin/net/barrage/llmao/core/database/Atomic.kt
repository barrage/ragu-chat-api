package net.barrage.llmao.core.database

import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

/** A repository with atomic operations. */
interface Atomic {
  val dsl: DSLContext

  /**
   * Start a transaction, supplying it to the provided `block` closure and using the `factory` to
   * wrap the context in the repository.
   *
   * Usage example:
   * ```kotlin
   * class Repository(private val context: DSLContext) {
   *   fun insertSomething() {
   *       // ...
   *   }
   * }
   *
   * class Service(val repository: Repository)  {
   *  fun doSomething() {
   *    repository.transaction(::Repository) { tx ->
   *      // tx has the same methods as Repository
   *      tx.insertSomething()
   *    }
   *  }
   * }
   * ```
   *
   * @param factory The "function" to use to create the repository from the transaction context. If
   *   the implementing repository has a single parameter constructor and that parameter is of type
   *   DSLContext, you can use `::Repository` as the factory.
   * @param block What to execute in the transaction context. Any exceptions thrown in this will
   *   revert the transaction.
   */
  suspend fun <T, R : Atomic> transaction(factory: (DSLContext) -> R, block: suspend (R) -> T): T =
    dsl.transactionCoroutine { tx -> block(factory(tx.dsl())) }
}
