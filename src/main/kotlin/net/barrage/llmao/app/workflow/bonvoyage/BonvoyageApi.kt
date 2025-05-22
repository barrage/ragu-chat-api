package net.barrage.llmao.app.workflow.bonvoyage

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.Email
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.blob.ATTACHMENTS_PATH
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.llm.ChatCompletionAgentParameters
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.types.KUUID
import java.io.ByteArrayOutputStream
import com.itextpdf.layout.element.Image as PdfImage

class BonvoyageAdminApi(
  private val repository: BonvoyageRepository,
  private val email: Email,
  private val settings: Settings,
  private val providers: ProviderState,
) {
  private val log = KtorSimpleLogger("n.b.l.a.workflow.bonvoyage.BonvoyageAdminApi")

  suspend fun listTrips(): List<BonvoyageTrip> = repository.listTrips()

  suspend fun getTripAggregate(id: KUUID): BonvoyageTripFullAggregate {
    val tripExpenses = repository.getTripAggregate(id)
    val messages = repository.getTripChatMessages(id)
    val welcomeMessage = repository.getTripWelcomeMessage(id)
    val reminders = repository.getStartAndEndTripReminders(id)
    return BonvoyageTripFullAggregate(
      trip = tripExpenses.trip,
      expenses = tripExpenses.expenses,
      chatMessages = messages,
      welcomeMessage = welcomeMessage,
      reminders = reminders,
    )
  }

  suspend fun addTravelManager(
    userId: String,
    userFullName: String,
    userEmail: String,
  ): BonvoyageTravelManager = repository.insertTravelManager(userId, userFullName, userEmail)

  suspend fun removeTravelManager(userId: String) = repository.deleteTravelManager(userId)

  suspend fun listTravelManagers(
    userId: String?
  ): List<BonvoyageTravelManagerUserMappingAggregate> =
    userId?.let { repository.listTravelManagers(userId) } ?: repository.listTravelManagers()

  suspend fun addTravelManagerUserMapping(
    insert: BonvoyageTravelManagerUserMappingInsert
  ): BonvoyageTravelManagerUserMapping =
    repository.insertTravelManagerUserMapping(
      insert.travelManagerId,
      insert.userId,
      insert.delivery,
    )

  suspend fun removeTravelManagerUserMapping(id: KUUID) =
    repository.deleteTravelManagerUserMapping(id)

  suspend fun listTravelRequests(
    status: BonvoyageTravelRequestStatus?
  ): List<BonvoyageTravelRequest> = repository.listTravelRequests(status = status)

  suspend fun approveTravelRequest(approval: ApproveTravelRequest): BonvoyageTrip {
    val request = repository.getTravelRequest(approval.requestId)

    if (request.status == BonvoyageTravelRequestStatus.APPROVED) {
      throw AppError.api(ErrorReason.InvalidOperation, "Request is already approved")
    }

    val trip =
      repository.transaction(::BonvoyageRepository) { repo ->
        repo.updateTravelRequestStatus(
          approval.requestId,
          approval.reviewerId,
          BonvoyageTravelRequestStatus.APPROVED,
          approval.reviewerComment,
        )

        val travelOrderId = createTravelOrder(request)

        val expectedStartTime = approval.expectedStartTime ?: request.expectedStartTime
        val expectedEndTime = approval.expectedEndTime ?: request.expectedEndTime

        val tripDetails =
          BonvoyageTripInsert(
            userId = request.userId,
            userFullName = request.userFullName,
            userEmail = request.userEmail,
            travelOrderId = travelOrderId,
            transportType = request.transportType,
            startLocation = request.startLocation,
            stops = request.stops,
            endLocation = request.endLocation,
            startDate = request.startDate,
            endDate = request.endDate,
            expectedStartTime = expectedStartTime,
            expectedEndTime = expectedEndTime,
            description = request.description,
            vehicleType = request.vehicleType,
            vehicleRegistration = request.vehicleRegistration,
            isDriver = request.isDriver,
          )

        val trip = repo.insertTrip(tripDetails)

        repo.updateTravelRequestWorkflow(approval.requestId, trip.id)

        trip
      }

    email.sendEmail(
      BonvoyageConfig.emailSender,
      request.userEmail,
      "Travel request approved",
      """Your travel request has been approved.
            |Travel order ID: ${trip.travelOrderId}
            |Trip ID: ${trip.id}"""
        .trimMargin(),
    )

    val welcomeMessage = getWelcomeMessage(trip)
    repository.insertTripWelcomeMessage(trip.id, welcomeMessage)

    return trip
  }

  private suspend fun getWelcomeMessage(trip: BonvoyageTrip): String {
    val bonvoyageModel = settings.get(BonvoyageModel.KEY)
    val bonvoyageLlmProvider = settings.get(BonvoyageLlmProvider.KEY)

    val defaultWelcomeMessage = defaultWelcomeMessage(trip)

    if (bonvoyageModel == null) {
      log.warn("No Bonvoyage model configured, using default welcome message")
      return defaultWelcomeMessage
    }

    if (bonvoyageLlmProvider == null) {
      log.warn("No Bonvoyage LLM provider configured, using default welcome message")
      return defaultWelcomeMessage
    }

    val provider = providers.llm.getOptional(bonvoyageLlmProvider)

    if (provider == null) {
      log.warn(
        "Bonvoyage LLM provider '$bonvoyageLlmProvider' not found, using default welcome message"
      )
      return defaultWelcomeMessage
    }

    if (!provider.supportsModel(bonvoyageModel)) {
      log.warn(
        "Bonvoyage LLM provider '$bonvoyageLlmProvider' does not support model '$bonvoyageModel', using default welcome message"
      )
      return defaultWelcomeMessage
    }

    val agent =
      BonvoyageWelcomeAgent(
        tokenTracker =
          TokenUsageTrackerFactory.newTracker(
            trip.userId,
            trip.userFullName,
            BONVOYAGE_WORKFLOW_ID,
            trip.id,
          ),
        model = bonvoyageModel,
        inferenceProvider = provider,
      )

    return agent.welcomeMessage(trip, defaultWelcomeMessage)
  }

  suspend fun rejectTravelRequest(requestId: KUUID, reviewerId: String, reviewerComment: String?) =
    repository.updateTravelRequestStatus(
      requestId,
      reviewerId,
      BonvoyageTravelRequestStatus.REJECTED,
      reviewerComment,
    )

  /** Returns the travel order ID. */
  private suspend fun createTravelOrder(travelRequest: BonvoyageTravelRequest): String {
    // TODO: implement when we get BC
    return KUUID.randomUUID().toString()
  }

  private fun defaultWelcomeMessage(trip: BonvoyageTrip): String {
    return """Hello ${trip.userFullName}, a trip from ${trip.startLocation} to ${trip.endLocation} has been created under ${trip.travelOrderId}.
      |Your trip starts on ${trip.startDate} and the ends on ${trip.endDate}."""
      .trimMargin()
  }
}

class BonvoyageUserApi(
  private val repository: BonvoyageRepository,
  private val email: Email,
  private val image: BlobStorage<Image>,
) {
  private val log = KtorSimpleLogger("n.b.l.a.workflow.bonvoyage.BonvoyageUserApi")

  suspend fun getTripWelcomeMessage(id: KUUID): BonvoyageTripWelcomeMessage? =
    repository.getTripWelcomeMessage(id)

  suspend fun getTripChatMessages(id: KUUID) = repository.getTripChatMessages(id)

  suspend fun insertMessages(id: KUUID, messages: List<MessageInsert>): KUUID =
    repository.insertWorkflowMessages(id, BONVOYAGE_WORKFLOW_ID, messages)

  suspend fun listTrips(userId: String): List<BonvoyageTrip> = repository.listTrips(userId)

  suspend fun updateTripReminders(
    id: KUUID,
    userId: String,
    update: BonvoyageTripUpdateReminders,
  ): Pair<BonvoyageTripNotification?, BonvoyageTripNotification?> {
    if (!repository.isTripOwner(id, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }

    val trip = repository.getTrip(id)

    // Reminders cannot be updated if the start or end times have been entered
    // since users are expected to have entered the times

    if (trip.startTime != null && update.expectedStartTime != PropertyUpdate.Undefined) {
      throw AppError.api(
        ErrorReason.InvalidOperation,
        "Cannot update expected start time; trip has already started",
      )
    }

    if (trip.endTime != null && update.expectedEndTime != PropertyUpdate.Undefined) {
      throw AppError.api(
        ErrorReason.InvalidOperation,
        "Cannot update expected end time; trip has already ended",
      )
    }

    val (startReminder, endReminder) = repository.getStartAndEndTripReminders(id)

    // Reminders cannot be updated if they have already been sent

    startReminder?.let {
      if (update.expectedStartTime != PropertyUpdate.Undefined && it.sentAt != null) {
        throw AppError.api(
          ErrorReason.InvalidOperation,
          "Cannot update expected start time; reminder has already been sent",
        )
      }
    }

    endReminder?.let {
      if (update.expectedEndTime != PropertyUpdate.Undefined && it.sentAt != null) {
        throw AppError.api(
          ErrorReason.InvalidOperation,
          "Cannot update expected end time; reminder has already been sent",
        )
      }
    }

    repository.updateTripReminders(id, update)

    return Pair(startReminder, endReminder)
  }

  suspend fun updateTrip(
    id: KUUID,
    userId: String,
    update: BonvoyageTripPropertiesUpdate,
  ): BonvoyageTrip {
    if (!repository.isTripOwner(id, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }
    return repository.updateTrip(id, update)
  }

  suspend fun getTrip(id: KUUID, userId: String): BonvoyageTrip = repository.getTrip(id, userId)

  suspend fun getTripAggregate(id: KUUID, userId: String): BonvoyageTripFullAggregate {
    if (!repository.isTripOwner(id, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }

    val tripExpenses = repository.getTripAggregate(id)
    val messages = repository.getTripChatMessages(id)
    val welcomeMessage = repository.getTripWelcomeMessage(id)
    val reminders = repository.getStartAndEndTripReminders(id)
    return BonvoyageTripFullAggregate(
      trip = tripExpenses.trip,
      expenses = tripExpenses.expenses,
      chatMessages = messages,
      welcomeMessage = welcomeMessage,
      reminders = reminders,
    )
  }

  suspend fun listTravelManagers(userId: String): List<BonvoyageTravelManagerUserMappingAggregate> =
    repository.listTravelManagers(userId)

  suspend fun requestTravelOrder(user: User, request: TravelRequest): BonvoyageTravelRequest {
    return repository.transaction(::BonvoyageRepository) { repo ->
      val req = repo.insertTravelRequest(user, request)
      val managers = repo.listTravelManagers(user.id)

      for (manager in managers) {
        for (mapping in manager.mappings) {
          when (mapping.delivery) {
            BonvoyageNotificationDelivery.EMAIL -> {
              // TODO: Use CC instead of sending email to each manager.
              email.sendEmail(
                BonvoyageConfig.emailSender,
                manager.manager.userEmail,
                "Travel request from ${user.username}",
                """A new travel request has been submitted by ${user.username}.
                  |Request ID: ${req.id}."""
                  .trimMargin(),
              )
            }

            BonvoyageNotificationDelivery.PUSH -> {
              // TODO: implement
            }
          }
        }
      }

      req
    }
  }

  suspend fun listTravelRequests(
    userId: String,
    status: BonvoyageTravelRequestStatus?,
  ): List<BonvoyageTravelRequest> = repository.listTravelRequests(userId, status)

  suspend fun getTravelRequest(id: KUUID, userId: String): BonvoyageTravelRequest =
    repository.getTravelRequest(id, userId)

  suspend fun uploadExpense(
    id: KUUID,
    user: User,
    image: IncomingMessageAttachment.Image,
    description: String?,
  ): Pair<MessageGroupAggregate, BonvoyageTravelExpense> {
    val trip = getTrip(id, user.id)
    val expenseAgent = BonvoyageWorkflowFactory.expenseAgent(user, id)
    val attachment = listOf(image)

    val content =
      if (description.isNullOrBlank()) {
        """The image is a receipt for an expense.
        | Use the information in the receipt to clarify the expense for accounting.
        | Use what is available on the receipt and nothing else.
        | If any locations are visible in receipt, be sure to include them in the description."""
          .trimMargin()
      } else {
        """The image is a receipt for an expense.
        | The user provided the following description:
        |
        | $description
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
      )

    val userMessage = response.first()
    val assistantMessage = response.last()

    assert(userMessage.role == "user")
    assert(assistantMessage.role == "assistant")

    if (assistantMessage.content == null) {
      throw AppError.internal("Bonvoyage received message without content")
    }

    val responseText = assistantMessage.content!!.text()
    val expense =
      try {
        Json.decodeFromString<TravelExpense>(responseText)
      } catch (e: SerializationException) {
        log.error("Failed to parse expense from agent response: $responseText", e)
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

    return repository.insertMessagesWithExpense(id, messageInsert, expenseInsert)
  }

  suspend fun updateExpense(
    tripId: KUUID,
    userId: String,
    expenseId: KUUID,
    update: BonvoyageTravelExpenseUpdateProperties,
  ): BonvoyageTravelExpense {
    if (!repository.isTripOwner(tripId, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }
    return repository.updateExpense(expenseId, update)
  }

  suspend fun listTripExpenses(tripId: KUUID, userId: String): List<BonvoyageTravelExpense> {
    if (!repository.isTripOwner(tripId, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }
    return repository.listTripExpenses(tripId)
  }

  suspend fun deleteTripExpense(tripId: KUUID, userId: String, expenseId: KUUID) {
    if (!repository.isTripOwner(tripId, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }
    repository.deleteTripExpense(expenseId)
  }

  suspend fun generateAndSendReport(tripId: KUUID, userId: String, userEmail: String) {
    if (!repository.isTripOwner(tripId, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }

    val trip = repository.getTripAggregate(tripId)

    val report = generateReportPdf(trip)

    email.sendEmailWithAttachment(
      BonvoyageConfig.emailSender,
      userEmail,
      trip.trip.travelOrderId,
      "Travel report for ${trip.trip.travelOrderId}.",
      report,
    )
  }

  private fun generateReportPdf(trip: BonvoyageTripExpenseAggregate): BonvoyageTripReport {
    if (trip.trip.startTime == null || trip.trip.endTime == null) {
      throw AppError.api(ErrorReason.InvalidOperation, "Trip is missing start or end time")
    }

    if (trip.trip.isDriver && (trip.trip.startMileage == null || trip.trip.endMileage == null)) {
      throw AppError.api(ErrorReason.InvalidOperation, "Trip is missing start or end mileage")
    }

    val documentBytes = ByteArrayOutputStream()
    val pdf = PdfDocument(PdfWriter(documentBytes))
    val document =
      Document(pdf, PageSize.A4).setFont(PdfFontFactory.createFont(BonvoyageConfig.fontPath))
    document.setMargins(20f, 20f, 20f, 20f)

    // Title

    val title = Paragraph("IZVJEŠTAJ SA SLUŽBENOG PUTA").simulateBold().setFontSize(12f)

    title.add(
      PdfImage(ImageDataFactory.create(BonvoyageConfig.logoPath))
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

    val startDate = trip.trip.startDate.toString()
    val startTime = trip.trip.startTime.toLocalTime().toString()
    val endDate = trip.trip.endDate.toString()
    val endTime = trip.trip.endTime.toString()
    val startLocation = trip.trip.startLocation
    val stops = trip.trip.stops
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

    travelInfoTable.addCell("Mjesta putovanja (odredišta)")
    travelInfoTable.addCell(Cell().add(Paragraph(stops.joinToString("-")).simulateBold()))

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
      Table(UnitValue.createPercentArray(floatArrayOf(0.1f, 0.4f, 0.4f, 0.1f)))
        .useAllAvailableWidth()
        .addCell("Iznos")
        .addCell("Opis")
        .addCell("Vrijeme")
        .addCell("Prilog")

    for ((i, expense) in trip.expenses.withIndex()) {
      expensesTable.addCell("${expense.amount} ${expense.currency}")
      expensesTable.addCell(expense.description)

      val date =
        "${expense.expenseCreatedAt.toLocalDate()} ${expense.expenseCreatedAt.toLocalTime()}"
      expensesTable.addCell(date)
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
}
