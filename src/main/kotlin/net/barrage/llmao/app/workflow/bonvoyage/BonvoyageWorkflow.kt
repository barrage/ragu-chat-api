package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.llm.ChatCompletionAgentParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.ResponseFormat
import net.barrage.llmao.core.llm.Tools
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
  private val expenseAgent: BonvoyageExpenseAgent,
  private val chatAgent: BonvoyageChatAgent,
  /** Output handle. */
  private val emitter: Emitter,
  private val api: BonvoyageUserApi,
    private val tools: Tools,
) : WorkflowRealTime<BonvoyageInput>(BonvoyageInput.serializer()) {
  override suspend fun handleInput(input: BonvoyageInput) = execute(input)

  private suspend fun execute(input: BonvoyageInput) {
    when (input) {
      is BonvoyageInput.ChatInput -> handleChatInput(input)
      is BonvoyageInput.ExpenseUpload -> handleExpenseUpload(input)
      is BonvoyageInput.ExpenseUpdate -> handleExpenseUpdate(input)
      is BonvoyageInput.TripSummary -> handleTripSummary()
      is BonvoyageInput.TripFinalize -> handleTripFinalize()
      is BonvoyageInput.TripGenerateReport -> handleTripGenerateReport()
    }
  }

  private suspend fun handleChatInput(input: BonvoyageInput.ChatInput) {
      val trip = api.getTrip(id, user.id)
    val message =
      input.input.attachments?.let { ChatMessageProcessor.toContentMulti(input.input.text, it) }
        ?: ContentSingle(input.input.text!!)

      val context =
      """You are a travel manager who is helping ${user.username} on his business trip
          | from ${trip.startLocation} to ${trip.endLocation}.
          | The following are the trip's stops:
          | 
          |${trip.stops.joinToString(" -\n")}
          |
          | The trip start time is ${trip.startDateTime} and the end time is ${trip.endDateTime}.
          | The description of the trip states the following:
          |
          | "${trip.description}"
          |
          | You provide the user with helpful information about the locations in the trip.
          """
        .trimMargin()

    val userMessage = ChatMessage.user(message)

    val (finishReason, messages) = chatAgent.collectAndForwardStream(context, userMessage, tools, emitter)

    if (messages.isEmpty()) {
      emitter.emit(StreamComplete(id, finishReason))
      return
    }

    val assistantMessage = messages.last()

    assert(assistantMessage.role == "assistant")
  }

  private suspend fun handleExpenseUpload(input: BonvoyageInput.ExpenseUpload) {
    val attachment = listOf(IncomingMessageAttachment.Image(input.data))

    val trip = api.getTrip(id, user.id)

    val content =
      if (input.description.isNullOrBlank()) {
        """The image is a receipt for an expense.
        | Use the information in the receipt to clarify the expense for accounting.
        | Use what is available on the receipt and nothing else.
        | If any locations are visible in receipt, be sure to include them in the description."""
          .trimMargin()
      } else {
        input.description
      }

    val context =
      """You are talking to ${user.username}.
          | ${user.username} is on a business trip from ${trip.startLocation} to ${trip.endLocation}.
          | The trip start time is ${trip.startDateTime} and the end time is ${trip.endDateTime}.
          | The description of the trip states the following:
          |
          | "${trip.description}"
          |
          | You keep track of expenses for this trip for the purpose of creating a trip report.
          | The user will send you pictures they took of receipts of expenses made on this trip.
          | They will also optionally provide you a concise description of the expense.
          |
          | You will extract the following information from the receipt image:
          | - The amount of money spent on the expense.
          | - The currency of the expense.
          | - The description of the expense. If the user provides a description, use it. Otherwise, attempt to describe it based on the image of the receipt.
          | - The date-time the expense was created at.
          |
          | You will output the extracted information in JSON format, using the schema ${EXPENSE_FORMAT.name}."""
        .trimMargin()

    val response =
      expenseAgent.completion(
        context,
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

    val messageInsert =
      listOf(userMessage.toInsert(attachments)) + listOf(assistantMessage.toInsert())
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
      api.insertMessagesWithExpense(id, user.id, messageInsert, expenseInsert)

    emitter.emit(BonvoyageOutput.ExpenseUpload(travelExpense), BonvoyageOutput.serializer())
    emitter.emit(StreamComplete(id, assistantMessage.finishReason!!, messageGroupId, attachments))
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
