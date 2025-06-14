package net.barrage.llmao.core.database

/**
 * A repository with atomic operations.
 *
 * @param C The type of the context used for atomic operations, i.e. a database executor interface.
 */
interface Atomic<C> {
  val context: C

  /**
   * Start a transaction, supplying it to the provided `block` closure and using the `factory` to
   * wrap the context in the repository.
   *
   * Usually, when dealing with databases such as Postgres, a transaction will provide the same
   * database operations API as the context it was created from. E.g. for JOOQ, this is the
   * DSLContext interface.
   *
   * Usage example:
   * ```kotlin
   * // C is an arbitrary, but concrete, type like Jooq's DSLContext.
   * class Repository(private val context: C) {
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
   *   C, you can use `::Repository` as the factory.
   * @param block What to execute in the transaction context. Any exceptions thrown in this will
   *   revert the transaction.
   */
  suspend fun <T, R : Atomic<C>> transaction(factory: (C) -> R, block: suspend (R) -> T): T
}
