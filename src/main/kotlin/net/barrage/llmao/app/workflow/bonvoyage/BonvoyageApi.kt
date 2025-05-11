package net.barrage.llmao.app.workflow.bonvoyage

import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.Email
import net.barrage.llmao.core.ErrorReason
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
  ): List<BonvoyageTravelManagerUserMappingAggregate> = userId?.let { repository.listTravelManagers(userId) } ?: repository.listTravelManagers()

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
          throw AppError.api(
              ErrorReason.InvalidOperation,
              "Request is already approved",
          )
      }

      val trip = repository.transaction(::BonvoyageRepository) { repo ->

          repo.updateTravelRequestStatus(
              requestId,
              reviewerId,
              BonvoyageTravelRequestStatus.APPROVED,
              reviewerComment,
          )

          val travelOrderId = requestTravelOrder(request)

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

    suspend fun rejectTravelRequest(
      requestId: KUUID,
      reviewerId: String,
      reviewerComment: String?,
    ) = repository.updateTravelRequestStatus(
      requestId,
      reviewerId,
      BonvoyageTravelRequestStatus.REJECTED,
      reviewerComment,
    )

  /** Returns the travel order ID. */
  private suspend fun requestTravelOrder(travelRequest: BonvoyageTravelRequest): String {
    // TODO: implement when we get BC
    return KUUID.randomUUID().toString()
  }
}

class BonvoyageTravelerApi(val repository: BonvoyageRepository, val email: Email) {
  suspend fun listTrips(userId: String): List<BonvoyageTrip> = repository.listTrips(userId)

  suspend fun getTripAggregate(id: KUUID, userId: String): BonvoyageTripAggregate =
    repository.getTripAggregate(id, userId)

  suspend fun listTravelManagers(userId: String): List<BonvoyageTravelManagerUserMappingAggregate> =
    repository.listTravelManagers(userId)

  suspend fun requestTravelOrder(user: User, request: TravelRequest) {
    repository.transaction(::BonvoyageRepository) { repo ->
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
    }
  }

  suspend fun listTravelRequests(
    userId: String,
    status: BonvoyageTravelRequestStatus?,
  ): List<BonvoyageTravelRequest> = repository.listTravelRequests(userId, status)
}
