package net.barrage.llmao.core.workflow

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason

/**
 * Implementation of a real-time workflow that handles cancellation and prevents input while a
 * stream is running.
 */
abstract class WorkflowRealTime<T>(protected val inputSerializer: KSerializer<T>) : Workflow {
    protected open val log = KtorSimpleLogger("n.b.l.c.workflow.WorkflowRealTime")

    /**
     * If this is not null, it means a stream is currently running and additional input will throw
     * until the stream is complete.
     */
    private var stream: Job? = null

    /** Scope in which [handleInput] runs. */
    protected val scope = CoroutineScope(Dispatchers.Default)

    /** Process the input. */
    protected abstract suspend fun handleInput(input: T)

    override fun execute(input: JsonElement) {
        if (stream != null) {
            throw AppError.api(ErrorReason.InvalidOperation, "Workflow is currently busy")
        }

        val input: T = Json.decodeFromJsonElement(inputSerializer, input)

        stream =
            scope.launch {
                runCatching { handleInput(input) }
                    .onFailure { e ->
                        log.error("{} - error in workflow stream", id, e)
                        stream = null
                    }
                    .onSuccess { stream = null }
            }
    }

    override fun cancelStream() {
        if (stream == null || stream?.isCancelled == true) {
            return
        }
        log.debug("{} - cancelling stream", id)
        stream!!.cancel()
    }
}
