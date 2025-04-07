package net.barrage.llmao.core.workflow

import kotlin.reflect.KClass

/** Handle for emitting realtime events from workflows. */
interface Emitter {
  /**
   * Emit a message.
   *
   * If the message being emitted is polymorphic (i.e. a sealed class), the `clazz` parameter must
   * be the parent sealed class.
   *
   * Otherwise, the `clazz` parameter can be omitted and the extension function [Emitter.emit] can
   * be used directly.
   */
  suspend fun <T : Any> emit(message: T, clazz: KClass<T>)
}
