package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ResponseFormat
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.emit
import net.barrage.llmao.types.KUUID

class TripotronWorkflow(
  /** Unique identifier of the workflow. */
  private val id: KUUID,
  private val tripotron: Tripotron,

  /** Current state of the workflow. */
  private var state: TripotronWorkflowState,

  /** Output handle. */
  private val emitter: Emitter,
  private val repository: TripotronRepository,
) : Workflow {
  private val scope = CoroutineScope(Dispatchers.Default)
  private var stream: Job? = null

  override fun id(): KUUID = id

  override fun execute(input: String) {
    val input = Json.decodeFromString<TripotronInput>(input)
    stream =
      scope.launch {
        runCatching { execute(input) }
          .onFailure { e ->
            if (e is AppError) emitter.emit(e)
            stream = null
          }
          .onSuccess { stream = null }
      }
  }

  private suspend fun execute(input: TripotronInput) {
    when (input) {
      is TripotronInput.ExpenseUpload -> handleExpenseInput(input)
      is TripotronInput.ExpenseList -> {}
      is TripotronInput.ExpenseVerify -> {}
      is TripotronInput.Finalize -> {}
    }
  }

  override fun cancelStream() {
    if (stream == null || stream?.isCancelled == true) {
      return
    }
    stream!!.cancel()
  }

  private suspend fun handleExpenseInput(input: TripotronInput.ExpenseUpload) {
    val attachment = listOf(IncomingMessageAttachment.Image(input.data))

    val response = tripotron.completion(input.description, attachment)

    val userMessage = response.first()
    val assistantMessage = response.last()

    assert(userMessage.role == "user")
    assert(assistantMessage.role == "assistant")

    val attachments = ChatMessageProcessor.storeMessageAttachments(attachment)

    val insert = listOf(userMessage.toInsert(attachments)) + listOf(assistantMessage.toInsert())

    repository.insertMessages(id, insert)
    // emitter.emit(TripotronOutput.ExpenseRegistered(expense))
  }
}

private val EXPENSE_FORMAT =
  ResponseFormat(
    name = "TravelExpenseUpload",
    schema =
      JsonObject(
        mapOf(
          "type" to JsonPrimitive("object"),
          "properties" to
            JsonObject(
              mapOf(
                "amount" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("number"),
                      "description" to
                        JsonPrimitive("The total amount of money spent indicated on the receipt."),
                    )
                  ),
                "currency" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to
                        JsonPrimitive(
                          "The currency of the expense as indicated on the receipt. If not indicated, default to EUR."
                        ),
                    )
                  ),
                "description" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to
                        JsonPrimitive(
                          """
                    A description of the expense. If the user provides a description, use it.
                    Otherwise, attempt to describe it based on the image of the receipt.
                    If still unclear, leave it empty."""
                            .trimMargin()
                        ),
                    )
                  ),
              )
            ),
          "additionalProperties" to JsonPrimitive(false),
          "required" to JsonArray(listOf(JsonPrimitive("amount"), JsonPrimitive("currency"))),
        )
      ),
  )

sealed class TripotronWorkflowState {
  /** Travel data has been sent and the workflow is accepting expense data. */
  data object Started : TripotronWorkflowState()
}
