package net.barrage.llmao.core.workflow

import net.barrage.llmao.core.AppError

/** Handle for emitting realtime events from workflows. */
interface Emitter<T> {
  /** Emit a message. */
  suspend fun emit(message: T)

  /** Emit an error. */
  suspend fun emitError(error: AppError)
}
