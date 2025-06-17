package net.barrage.llmao.core.workflow

import kotlinx.serialization.KSerializer
import net.barrage.llmao.core.AppError

/** Handle for emitting realtime events from workflows. */
interface Emitter {
  /**
   * Emit a message.
   *
   * If the message being emitted is polymorphic (i.e. a sealed class), the `serializer` parameter
   * should be the parent so it retains the `type` discriminator.
   */
  suspend fun <T> emit(message: T, serializer: KSerializer<T>)

  /**
   * Free implementation of serializing and emitting workflow output data.
   *
   * All workflow output variants must be registered via its [plugin serialization configuration]
   * [net.barrage.llmao.core.net.barrage.llmao.core.net.barrage.llmao.core.Plugin.configureOutputSerialization].
   *
   * See [emit].
   */
  suspend fun emit(message: WorkflowOutput) = emit(message, WorkflowOutput.serializer())

  /**
   * Free implementation of serializing and emitting errors.
   *
   * See [emit].
   */
  suspend fun emit(error: AppError) = emit(error, AppError.serializer())
}
