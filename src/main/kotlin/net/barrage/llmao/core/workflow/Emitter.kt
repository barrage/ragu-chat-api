package net.barrage.llmao.core.workflow

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** Handle for emitting realtime events from workflows. */
interface Emitter {
  /**
   * Emit a message.
   *
   * If the message being emitted is polymorphic (i.e. a sealed class), the `serializer` parameter
   * should be the parent sealed class so it retains the `type` discriminator.
   *
   * Otherwise, the `serializer` parameter can be omitted and the extension function [Emitter.emit]
   * can be used directly.
   */
  suspend fun <T> emit(message: T, serializer: KSerializer<T>)
}

suspend inline fun <reified T> Emitter.emit(data: T) {
  this.emit(data, serializer<T>())
}
