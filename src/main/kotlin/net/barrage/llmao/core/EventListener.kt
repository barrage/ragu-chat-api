package net.barrage.llmao.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import net.barrage.llmao.core.types.KUUID

/**
 * An event listener for propagating application events to the chat server. The handler for events
 * is set with [start].
 */
class EventListener<T> {
  private val q: MutableSharedFlow<T> = MutableSharedFlow()
  private var job: Job? = null

  /**
   * Starts the event listener job using the given handler to handle events. Must be called only
   * once or it will throw.
   */
  fun start(handler: suspend (T) -> Unit) {
    if (job != null) {
      throw IllegalStateException("Event listener already started")
    }

    job = CoroutineScope(Dispatchers.Default).launch { q.collect { event -> handler(event) } }
  }

  /** Dispatch an event to the listener. Must be called after [start] or it will throw. */
  suspend fun dispatch(event: T) {
    if (job == null) {
      throw IllegalStateException(
        "Event listener not started; Ensure you are calling 'start' first."
      )
    }

    q.emit(event)
  }
}

sealed class StateChangeEvent {
  data class AgentDeactivated(val agentId: KUUID) : StateChangeEvent()
}
