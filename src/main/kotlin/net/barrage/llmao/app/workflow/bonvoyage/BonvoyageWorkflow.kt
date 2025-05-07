package net.barrage.llmao.app.workflow.bonvoyage

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.element.Image as PdfImage
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import io.ktor.util.logging.KtorSimpleLogger
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.Email
import net.barrage.llmao.core.blob.ATTACHMENTS_PATH
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatCompletionAgentParameters
import net.barrage.llmao.core.llm.ResponseFormat
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.core.workflow.emit
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

class BonvoyageWorkflow(
  /** Unique identifier of the workflow. */
  private val id: KUUID,
  private val bonvoyage: Bonvoyage,
  /** Output handle. */
  private val emitter: Emitter,
  private val repository: BonvoyageRepository,
  private val email: Email,
  private val image: BlobStorage<Image>,
) : Workflow {
  private val scope = CoroutineScope(Dispatchers.Default)
  private var stream: Job? = null
  private val log = KtorSimpleLogger("n.b.l.a.workflow.bonvoyage.TripotronWorkflow")

  override fun id(): KUUID = id

  override fun execute(input: String) {
    val input = Json.decodeFromString<BonvoyageInput>(input)
    stream =
      scope.launch {
        runCatching { execute(input) }
          .onFailure { e ->
            log.error("error in stream", e)
            if (e is AppError) emitter.emit(e)
            stream = null
          }
          .onSuccess { stream = null }
      }
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

  override fun cancelStream() {
    if (stream == null || stream?.isCancelled == true) {
      return
    }
    stream!!.cancel()
  }

  private suspend fun handleExpenseUpload(input: BonvoyageInput.ExpenseUpload) {
    val trip = repository.getTrip(id)

    val attachment = listOf(IncomingMessageAttachment.Image(input.data))
    bonvoyage.setContext(tripExpenseContext(trip))

    val response =
      bonvoyage.completion(
        input.description,
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
      repository.insertMessagesWithExpense(id, insert, expenseInsert)

    emitter.emit(
      WorkflowOutput.StreamComplete(
        id,
        assistantMessage.finishReason!!,
        messageGroupId,
        attachments,
      ),
      WorkflowOutput.serializer(),
    )
    emitter.emit(BonvoyageOutput.ExpenseUpload(travelExpense), BonvoyageOutput.serializer())
  }

  private suspend fun handleExpenseUpdate(input: BonvoyageInput.ExpenseUpdate) {
    val updatedExpense = repository.updateExpense(input.expenseId, input.properties)
    emitter.emit(BonvoyageOutput.ExpenseUpdate(updatedExpense), BonvoyageOutput.serializer())
  }

  private suspend fun handleTripSummary() {
    val trip = repository.getTripAggregate(id)
    emitter.emit(BonvoyageOutput.TripSummary(trip), BonvoyageOutput.serializer())
  }

  private suspend fun handleTripFinalize() {
    val expenses = repository.listTripExpenses(id)

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
    val trip = repository.getTripAggregate(id)
    val report = generateReport(trip)

    email.sendEmailWithAttachment(
      "bonvoyage@barrage.net",
      // Safe to !! because the factory always verifies this is not null
      bonvoyage.user.email!!,
      trip.trip.travelOrderId,
      "Here is your travel report for ${trip.trip.travelOrderId}",
      report,
    )

    emitter.emit(BonvoyageOutput.TripReport, BonvoyageOutput.serializer())
  }

  private fun generateReport(trip: BonvoyageTripAggregate): BonvoyageTripReport {
    val documentBytes = ByteArrayOutputStream()
    val pdf = PdfDocument(PdfWriter(documentBytes))
    val document =
      Document(pdf, PageSize.A4)
        .setFont(PdfFontFactory.createFont("./src/main/resources/bonvoyage/DejaVuSans.ttf"))
    document.setMargins(20f, 20f, 20f, 20f)

    // Title

    val title = Paragraph("IZVJEŠTAJ SA SLUŽBENOG PUTA").simulateBold().setFontSize(12f)

    title.add(
      PdfImage(ImageDataFactory.create("./src/main/resources/bonvoyage/logo.png"))
        .setWidth(60f)
        .setHeight(25f)
        .setMarginLeft(300f)
        .setRelativePosition(0f, 5f, 0f, 0f)
    )
    title.setMarginBottom(10f)

    document.add(title)

    document.add(LineSeparator(SolidLine(0.25f)).setMarginBottom(10f))

    // Worker info and travel order

    val workerInfoTable =
      Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f))).useAllAvailableWidth()

    workerInfoTable.addCell(Cell().add(Paragraph("RADNIK (Ime i prezime)")))
    workerInfoTable.addCell(Cell().add(Paragraph(trip.trip.userFullName)).simulateBold())

    workerInfoTable.addCell(Cell().add(Paragraph("PUTNI NALOG (Broj putnog naloga)")))
    workerInfoTable.addCell(Cell().add(Paragraph(trip.trip.travelOrderId)).simulateBold())

    workerInfoTable.setMarginBottom(20f)

    document.add(workerInfoTable)

    // Travel info

    val startDate = trip.trip.startDateTime.toLocalDate().toString()
    val startTime = trip.trip.startDateTime.toLocalTime().toString()
    val endDate = trip.trip.endDateTime.toLocalDate().toString()
    val endTime = trip.trip.endDateTime.toLocalTime().toString()
    val startLocation = trip.trip.startLocation
    val destination = trip.trip.destination
    val endLocation = trip.trip.endLocation

    val travelInfoTable =
      Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f))).useAllAvailableWidth()

    travelInfoTable.addCell("Datum po\u010Detka puta")
    travelInfoTable.addCell(Cell().add(Paragraph(startDate).simulateBold()))

    travelInfoTable.addCell("Vrijeme početka puta")
    travelInfoTable.addCell(Cell().add(Paragraph(startTime).simulateBold()))

    travelInfoTable.addCell("Datum završetka puta")
    travelInfoTable.addCell(Cell().add(Paragraph(endDate).simulateBold()))

    travelInfoTable.addCell("Vrijeme završetka puta")
    travelInfoTable.addCell(Cell().add(Paragraph(endTime).simulateBold()))

    travelInfoTable.addCell("Mjesto po\u010Detka puta")
    travelInfoTable.addCell(Cell().add(Paragraph(startLocation).simulateBold()))

    travelInfoTable.addCell("Mjesto putovanja (odredište)")
    travelInfoTable.addCell(Cell().add(Paragraph(destination).simulateBold()))

    travelInfoTable.addCell("Mjesto završetka puta")
    travelInfoTable.addCell(Cell().add(Paragraph(endLocation).simulateBold()))

    travelInfoTable.setMarginBottom(20f)

    document.add(travelInfoTable)

    // Report section
    document.add(Paragraph("IZVJEŠTAJ").setMarginTop(20f).simulateBold())
    document.add(Paragraph(trip.trip.description))

    document.add(LineSeparator(SolidLine(0.25f)).setMarginBottom(10f))

    document.add(Paragraph("PRILOZI").setMarginTop(20f).simulateBold())

    val expensesTable =
      Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f, 1f)))
        .useAllAvailableWidth()
        .addCell("Iznos")
        .addCell("Valuta")
        .addCell("Opis")
        .addCell("Prilog")

    for ((i, expense) in trip.expenses.withIndex()) {
      expensesTable.addCell(expense.amount.toString())
      expensesTable.addCell(expense.currency)
      expensesTable.addCell(expense.description)
      expensesTable.addCell((i + 1).toString())
    }

    document.add(expensesTable)
    document.add(AreaBreak())

    for ((i, expense) in trip.expenses.withIndex()) {
      document.pdfDocument.addNewPage()
      document.add(Paragraph("Prilog ${i + 1}:"))

      val image = image.retrieve("$ATTACHMENTS_PATH/${expense.imagePath}")
      if (image == null) {
        document.add(Paragraph("SLIKA NEDOSTUPNA"))
        continue
      }

      document.add(PdfImage(ImageDataFactory.create(image.data)).setAutoScale(true))
      document.add(AreaBreak())
    }

    if (document.pdfDocument.lastPage.contentBytes.isEmpty()) {
      document.pdfDocument.removePage(document.pdfDocument.lastPage)
    }

    document.flush()

    document.close()

    return BonvoyageTripReport(
      bytes = documentBytes.toByteArray(),
      travelOrderId = trip.trip.travelOrderId,
    )
  }

  private fun tripExpenseContext(trip: BonvoyageTrip): String {
    val username = bonvoyage.user.username
    return """
          You are talking to $username. 
          $username is on a business trip from ${trip.startLocation} to ${trip.endLocation}.
          The trip start time is ${trip.startDateTime} and the end time is ${trip.endDateTime}. 
          The description of the trip states the following:
          
          "${trip.description}"
          
          Your job is to keep track of expenses for this trip for the purpose of creating a trip report. 
          The user will send you pictures they took of receipts of expenses made on this trip.
          They will also provide you a description of the expense.
          
          You will extract the following information from the receipt image:
          - The amount of money spent on the expense.
          - The currency of the expense.
          - The description of the expense.
          - The date-time the expense was created at.
          
          You will output the extracted information in JSON format, using the schema ${EXPENSE_FORMAT.name}.
      """
      .trimIndent()
  }
}

private val EXPENSE_FORMAT_SCHEMA =
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

private val EXPENSE_FORMAT =
  ResponseFormat(name = "TravelExpenseUpload", schema = EXPENSE_FORMAT_SCHEMA)
