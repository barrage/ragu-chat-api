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
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

/** Real time workflow used for chatting and for uploading/editing expenses. */
class BonvoyageWorkflow(
  /** Unique identifier of the trip. */
  override val id: KUUID,
  private val user: User,
  private val expenseAgent: BonvoyageExpenseAgent,
  private val chatAgent: BonvoyageChatAgent,
  /** Output handle. */
  private val emitter: Emitter,
  private val api: BonvoyageUserApi,
  private val tools: Tools,
) : WorkflowRealTime<BonvoyageInput>(BonvoyageInput.serializer()) {
  override suspend fun handleInput(input: BonvoyageInput) {
    when (input) {
      is BonvoyageInput.ChatInput -> handleChatInput(input)
      is BonvoyageInput.ExpenseUpload -> handleExpenseUpload(input)
      is BonvoyageInput.ExpenseUpdate -> handleExpenseUpdate(input)
    }
  }

  private suspend fun handleChatInput(input: BonvoyageInput.ChatInput) {
    val trip = api.getTrip(id, user.id)
    val message =
      input.input.attachments?.let { ChatMessageProcessor.toContentMulti(input.input.text, it) }
        ?: ContentSingle(input.input.text!!)

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
      input.input.attachments?.let { ChatMessageProcessor.storeMessageAttachments(it) }
    val userMessageInsert = userMessage.toInsert(attachmentsInsert)
    val messageGroupId =
      api.insertMessages(id, listOf(userMessageInsert) + messages.map { it.toInsert() })

    emitter.emit(StreamComplete(id, finishReason, messageGroupId, attachmentsInsert))
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
        """The image is a receipt for an expense.
        | The user provided the following description:
        |
        | ${input.description}
        |
        | If the user provided the purpose of the expense and the location it was made,
        | do not modify it and use it directly.
        | Otherwise use the information in the receipt to enrich the description with the location.
        | """
          .trimMargin()
      }

    val context =
      """You are talking to ${user.username}.
          | ${user.username} is on a business trip from ${trip.startLocation} to ${trip.endLocation}.
          |
          | You keep track of expenses for this trip for the purpose of creating a trip report.
          | The user will send you pictures they took of receipts of expenses made on this trip.
          | They will also optionally provide you a concise description of the expense.
          |
          | You will extract the following information from the receipt image:
          | - The amount of money spent on the expense.
          | 
          | - The currency of the expense.
          | 
          | - The description of the expense. 
          |   If the user provides a description, use it and enrich it with any information from the receipt,
          |   such as specifying the locations on it.
          |   If they do not provide the description, attempt to describe it based on the image of the receipt.
          |   
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
        throw AppError.internal("Failed to parse expense from agent response", original = e)
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

    val travelExpense = api.insertExpenseMessages(id, messageInsert, expenseInsert)

    emitter.emit(ExpenseUpload(travelExpense))
    emitter.emit(
      StreamComplete(id, assistantMessage.finishReason!!, travelExpense.messageGroupId, attachments)
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
