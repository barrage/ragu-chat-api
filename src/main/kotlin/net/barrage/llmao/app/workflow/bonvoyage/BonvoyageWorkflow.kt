package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID

/** Real time workflow used for chatting and for uploading/editing expenses. */
class BonvoyageWorkflow(
  /** Unique identifier of the trip. */
  override val id: KUUID,
  private val user: User,
  private val chatAgent: BonvoyageChatAgent,
  /** Output handle. */
  private val emitter: Emitter,
  private val api: BonvoyageUserApi,
  private val tools: Tools,
) : WorkflowRealTime<BonvoyageInput>(BonvoyageInput.serializer()) {
  override suspend fun handleInput(input: BonvoyageInput) {
    when (input) {
      is BonvoyageInput.Chat -> handleChatInput(input)
      is BonvoyageInput.ExpenseUpload -> handleExpenseUpload(input)
      is BonvoyageInput.ExpenseUpdate -> handleExpenseUpdate(input)
    }
  }

  private suspend fun handleChatInput(input: BonvoyageInput.Chat) {
    input.message.validate()

    val trip = api.getTrip(id, user.id)
    val message =
      input.message.attachments?.let { ChatMessageProcessor.toContentMulti(input.message.text, it) }
        ?: ContentSingle(input.message.text!!)

    val context =
      """You are a travel chat assistant who is helping ${user.username} on his business trip
          | from ${trip.startLocation} to ${trip.endLocation}.
          | The following are the trip's stops:
          | 
          |${trip.stops.joinToString(" -\n")}
          |
          | The trip start date is ${trip.startDate} and the end date is ${trip.endDate}.
          | The description of the trip states the following:
          |
          | "${trip.description}"
          |
          | You are one part of a trip management system with the following capabilities:
          |   - Expense management; Users can upload images of receipts from expenses they incur on the trip 
          |     for the purposes of reimbursement.
          |   - Start and end time verification; It is of utmost importance users correctly report the start and end
          |     times of the trip because incorrect reporting adversely affect the reimbursement process and damage
          |     both the employee and the company.
          |   - Trip reporting; Users can request a report of the trip which will be sent to them via email.
          |
          | You provide the user with helpful information about their trip, especially if it relates to any of the 
          | trip locations. You direct the user to the appropriate capabilities of the system.
          | 
          | The user can use the system to edit the actual start and end times of when they have departed and arrived.
          | The departure time is the exact time the user exits their residence at the start location.
          | The arrival time is the exact time the user arrives to the residence of the end location.
          | Note the key word here is residence. 
          | The user is considered to partake in the trip even if they are still at the start location, but have left
          | their current residence in it. The same applies to end locations for when they have not yet arrived to the
          | residence, but have entered the end location.
          | The user entered the departure time of ${trip.startTime}.
          | The user entered the arrival time of ${trip.endTime}.
          | The current time is ${KOffsetDateTime.now()}.
          | If the trip has started and the user has not entered the departure time, remind them to do so.
          | If the trip has ended and the user has not entered the arrival time, remind them to do so.
          """
        .trimMargin()

    val userMessage = ChatMessage.user(message)

    val (finishReason, messages) =
      chatAgent.collectAndForwardStream(context, userMessage, tools, emitter)

    val attachmentsInsert =
      input.message.attachments?.let { ChatMessageProcessor.storeMessageAttachments(it) }
    val userMessageInsert = userMessage.toInsert(attachmentsInsert)
    val messageGroupId =
      api.insertMessages(id, listOf(userMessageInsert) + messages.map { it.toInsert() })

    emitter.emit(StreamComplete(id, finishReason, messageGroupId, attachmentsInsert))
  }

  private suspend fun handleExpenseUpload(input: BonvoyageInput.ExpenseUpload) {
    val (group, travelExpense) =
      api.uploadExpense(id, user, IncomingMessageAttachment.Image(input.data), input.description)
    val userMessage = group.messages.first()
    val assistantMessage = group.messages.last()
    emitter.emit(ExpenseUpload(travelExpense))
    emitter.emit(
      StreamComplete(
        id,
        assistantMessage.finishReason!!,
        travelExpense.messageGroupId,
        userMessage.attachments,
      )
    )
  }

  private suspend fun handleExpenseUpdate(input: BonvoyageInput.ExpenseUpdate) {
    val updatedExpense = api.updateExpense(id, user.id, input.expenseId, input.properties)
    emitter.emit(ExpenseUpdate(updatedExpense))
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
