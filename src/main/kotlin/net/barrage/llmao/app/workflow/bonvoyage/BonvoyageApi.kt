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
import com.itextpdf.layout.element.Image as PdfImage
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.Email
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.blob.ATTACHMENTS_PATH
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.User
import net.barrage.llmao.types.KUUID

class BonvoyageAdminApi(val repository: BonvoyageRepository, val email: Email) {
  suspend fun listTrips(): List<BonvoyageTrip> = repository.listTrips()

  suspend fun getTripAggregate(id: KUUID): BonvoyageTripAggregate = repository.getTripAggregate(id)

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

  suspend fun approveTravelRequest(
    requestId: KUUID,
    reviewerId: String,
    reviewerComment: String?,
  ): BonvoyageTrip {
    val request = repository.getTravelRequest(requestId)

    if (request.status == BonvoyageTravelRequestStatus.APPROVED) {
      throw AppError.api(ErrorReason.InvalidOperation, "Request is already approved")
    }

    val trip =
      repository.transaction(::BonvoyageRepository) { repo ->
        repo.updateTravelRequestStatus(
          requestId,
          reviewerId,
          BonvoyageTravelRequestStatus.APPROVED,
          reviewerComment,
        )

        val travelOrderId = createTravelOrder(request)

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
            startDateTime = request.startDateTime,
            endDateTime = request.endDateTime,
            description = request.description,
            vehicleType = request.vehicleType,
            vehicleRegistration = request.vehicleRegistration,
          )

        val trip = repo.insertTrip(tripDetails)

        repo.updateTravelRequestWorkflow(requestId, trip.id)

        trip
      }

    email.sendEmail(
      "bonvoyage@barrage.net",
      request.userEmail,
      "Travel request approved",
      """Your travel request has been approved.
            |Travel order ID: ${trip.travelOrderId}
            |Trip ID: ${trip.id}"""
        .trimMargin(),
    )

    return trip
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
}

class BonvoyageUserApi(
  val repository: BonvoyageRepository,
  val email: Email,
  val image: BlobStorage<Image>,
) {
  suspend fun insertMessagesWithExpense(
    id: KUUID,
    userId: String,
    messages: List<MessageInsert>,
    expense: BonvoyageTravelExpenseInsert,
  ): Pair<KUUID, BonvoyageTravelExpense> {
    if (!repository.isTripOwner(id, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }

    return repository.insertMessagesWithExpense(id, messages, expense)
  }

  suspend fun listTrips(userId: String): List<BonvoyageTrip> = repository.listTrips(userId)

  suspend fun updateTripProperties(
    id: KUUID,
    userId: String,
    update: BonvoyageTripUpdate,
  ): BonvoyageTrip {
    if (!repository.isTripOwner(id, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }
    return repository.updateTrip(id, update)
  }

  suspend fun getTrip(id: KUUID, userId: String): BonvoyageTrip {
    if (!repository.isTripOwner(id, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }
    return repository.getTrip(id)
  }

  suspend fun getTripAggregate(id: KUUID, userId: String): BonvoyageTripAggregate =
    repository.getTripAggregate(id, userId)

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
                "bonvoyage@barrage.net",
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

  suspend fun generateAndSendReport(tripId: KUUID, userId: String, userEmail: String) {
    if (!repository.isTripOwner(tripId, userId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
    }

    val trip = repository.getTripAggregate(tripId)

    val report = generateReportPdf(trip)

    email.sendEmailWithAttachment(
      "bonvoyage@barrage.net",
      // Safe to !! because the factory always verifies this is not null
      userEmail,
      trip.trip.travelOrderId,
      "Travel report for ${trip.trip.travelOrderId}.",
      report,
    )
  }

  private fun generateReportPdf(trip: BonvoyageTripAggregate): BonvoyageTripReport {
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
