package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.llm.ChatCompletionAgentParameters
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.ResponseFormat
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.StreamComplete
import net.barrage.llmao.core.workflow.WorkflowRealTime
import net.barrage.llmao.types.KUUID

class BonvoyageWorkflow(
  /** Unique identifier of the workflow. */
  override val id: KUUID,
  private val user: User,
  private val bonvoyageExpenseAgent: BonvoyageExpenseAgent,
  /** Output handle. */
  private val emitter: Emitter,
  private val api: BonvoyageUserApi,
) : WorkflowRealTime<BonvoyageInput>(BonvoyageInput.serializer()) {
  override suspend fun handleInput(input: BonvoyageInput) {
    execute(input)
  }

  private suspend fun execute(input: BonvoyageInput) {
    when (input) {
      is BonvoyageInput.ExpenseUpload -> handleExpenseUpload(input)
      is BonvoyageInput.ExpenseUpdate -> handleExpenseUpdate(input)
      is BonvoyageInput.TripSummary -> handleTripSummary()
      is BonvoyageInput.TripFinalize -> handleTripFinalize()
      is BonvoyageInput.TripGenerateReport -> handleTripGenerateReport()
    }
  }

  private suspend fun handleExpenseUpload(input: BonvoyageInput.ExpenseUpload) {
    val attachment = listOf(IncomingMessageAttachment.Image(input.data))

    val content =
      if (input.description.isNullOrBlank()) {
        """The image is a receipt for an expense.
        | Use the information in the receipt to clarify the expense for accounting.
        | Use what is available on the receipt and nothing else.
        | If the image is of a highway toll, or of a ticket for public transport, include
        | the start and end stops in the description, if they are available."""
          .trimMargin()
      } else {
        input.description
      }

    val response =
      bonvoyageExpenseAgent.completion(
        content,
        attachment,
        ChatCompletionAgentParameters(responseFormat = EXPENSE_FORMAT),
        emitter,
      )

    val userMessage = response.first()
    val assistantMessage = response.last()

    println(assistantMessage)

    assert(userMessage.role == "user")
    assert(assistantMessage.role == "assistant")

    if (assistantMessage.content == null) {
      throw AppError.internal("Bonvoyage received message without content")
    }

    val expense =
      try {
        Json.decodeFromString<TravelExpense>(assistantMessage.content!!.text())
      } catch (e: SerializationException) {
        throw AppError.internal("Failed to parse expense from Bonvoyage response", original = e)
      }

    val attachments =
      try {
        ChatMessageProcessor.storeMessageAttachments(attachment)
      } catch (e: Exception) {
        throw AppError.internal("Failed to store message attachments", original = e)
      }

    val expenseImage = attachments.first()

    val insert = listOf(userMessage.toInsert(attachments)) + listOf(assistantMessage.toInsert())
    val expenseInsert =
      BonvoyageTravelExpenseInsert(
        amount = expense.amount,
        currency = expense.currency,
        description = expense.description,
        imagePath = expenseImage.url,
        imageProvider = expenseImage.provider,
        expenseCreatedAt = expense.createdAt,
      )

    val (messageGroupId, travelExpense) =
      api.insertMessagesWithExpense(id, user.id, insert, expenseInsert)

    emitter.emit(StreamComplete(id, assistantMessage.finishReason!!, messageGroupId, attachments))
    emitter.emit(BonvoyageOutput.ExpenseUpload(travelExpense), BonvoyageOutput.serializer())
  }

  private suspend fun handleExpenseUpdate(input: BonvoyageInput.ExpenseUpdate) {
    val updatedExpense = api.updateExpense(id, user.id, input.expenseId, input.properties)
    emitter.emit(BonvoyageOutput.ExpenseUpdate(updatedExpense), BonvoyageOutput.serializer())
  }

  private suspend fun handleTripSummary() {
    val trip = api.getTripAggregate(id, user.id)
    emitter.emit(BonvoyageOutput.TripSummary(trip), BonvoyageOutput.serializer())
  }

  private suspend fun handleTripFinalize() {
    val expenses = api.listTripExpenses(id, user.id)

    val unverifiedExpenses = expenses.filter { !it.verified }

    if (unverifiedExpenses.isNotEmpty()) {
      emitter.emit(
        BonvoyageOutput.FinalizeTripExpensesUnverified(unverifiedExpenses),
        BonvoyageOutput.serializer(),
      )
      return
    }
  }

  private suspend fun handleTripGenerateReport() {
    api.generateAndSendReport(id, user.id, user.email)
    emitter.emit(BonvoyageOutput.TripReport, BonvoyageOutput.serializer())
  }
}

internal val EXPENSE_FORMAT_SCHEMA =
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
                      """A description of the expense.
                        | If the user provides a description, use it.
                        | Otherwise, attempt to describe it based on the image of the receipt.
                        | Use any information in the receipt to clarify the expense for accounting.
                        | If still unclear, leave it empty."""
                        .trimMargin()
                    ),
                )
              ),
            "createdAt" to
              JsonObject(
                mapOf(
                  "type" to JsonPrimitive("string"),
                  "format" to JsonPrimitive("date-time"),
                  "description" to
                    JsonPrimitive(
                      """The date and time the expense was created, as indicated by the receipt.
                        | If not indicated, default to the current date and time. Always include
                        | the time zone offset. Use the language of the receipt to determine the
                        | time zone offset and default to UTC if you cannot determine it."""
                        .trimMargin()
                    ),
                )
              ),
          )
        ),
      "additionalProperties" to JsonPrimitive(false),
      "required" to
        JsonArray(
          listOf(JsonPrimitive("amount"), JsonPrimitive("currency"), JsonPrimitive("createdAt"))
        ),
    )
  )

internal val EXPENSE_FORMAT =
  ResponseFormat(name = "TravelExpenseUpload", schema = EXPENSE_FORMAT_SCHEMA)
