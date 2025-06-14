package net.barrage.llmao.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * An event listener for propagating application events to the chat server. The handler for events
 * is set with [start].
 */
class EventListener {
  private val q: MutableSharedFlow<Event> = MutableSharedFlow()
  private val scope = CoroutineScope(Dispatchers.Default)
  private var job: Job? = null

  /**
   * Starts the event listener job using the given handler to handle events. Must be called only
   * once or it will throw.
   */
  internal fun start(handler: suspend (Event) -> Unit) {
    if (job != null) {
      throw IllegalStateException("Event listener already started")
    }

    job = scope.launch { q.collect { event -> handler(event) } }
  }

  /** Dispatch an event to the listener. Must be called after [start] or it will throw. */
  suspend fun dispatch(event: Event) {
    if (job == null) {
      throw IllegalStateException(
        "Event listener not started; Ensure you are calling 'start' first."
      )
    }

    q.emit(event)
  }
}

/** Marker interface for events. */
interface Event
