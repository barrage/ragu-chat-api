package net.barrage.llmao.core.session

import net.barrage.llmao.error.AppError

/** Handle for emitting realtime events from sessions. */
interface Emitter<T> {
  /** Emit a message. */
  suspend fun emit(message: T)

  /** Emit an error. */
  suspend fun emitError(error: AppError)

  /**
   * Close the emitter. Usually emitter implementations will be flows, so this will close the flow.
   */
  fun close()
}
