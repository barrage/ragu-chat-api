package net.barrage.llmao.app.workflow.bonvoyage

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.util.encodeBase64
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import net.barrage.llmao.app.http.pathUuid
import net.barrage.llmao.app.http.queryParam
import net.barrage.llmao.app.http.user
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.core.model.IncomingMessageAttachment

fun Route.bonvoyageAdminRoutes(api: BonvoyageAdminApi) {
  route("/bonvoyage/admin") {
    route("/trips") {
      get { call.respond(api.listTrips()) }

      get("/{id}") {
        val id = call.pathUuid("id")
        val trip = api.getTripAggregate(id)
        call.respond(trip)
      }

      route("/requests") {
        get {
          val status = call.queryParam("status")?.let { BonvoyageTravelRequestStatus.valueOf(it) }
          call.respond(api.listTravelRequests(status))
        }

        post {
          val request = call.receive<BonvoyageTravelRequestStatusUpdate>()
          val user = call.user()

          when (request.status) {
            BonvoyageTravelRequestStatus.APPROVED -> {
              val approval =
                ApproveTravelRequest(
                  requestId = request.id,
                  reviewerId = user.id,
                  reviewerComment = request.reviewComment,
                  expectedStartTime = request.expectedStartTime,
                  expectedEndTime = request.expectedEndTime,
                )
              val trip = api.approveTravelRequest(approval)
              call.respond(trip)
            }

            BonvoyageTravelRequestStatus.REJECTED -> {
              api.rejectTravelRequest(request.id, user.id, request.reviewComment)
              call.respond(HttpStatusCode.NoContent)
            }

            else -> throw AppError.api(ErrorReason.InvalidOperation, "Invalid status")
          }
        }
      }
    }

    route("/managers") {
      get {
        val userId = call.queryParam("userId")
        call.respond(api.listTravelManagers(userId))
      }

      post {
        val add = call.receive<BonvoyageTravelManagerInsert>()
        val manager = api.addTravelManager(add.userId, add.userFullName, add.userEmail)
        call.respond(manager)
      }

      delete("/{userId}") {
        val userId = call.pathParameters["userId"]!!
        api.removeTravelManager(userId)
        call.respond(HttpStatusCode.NoContent)
      }
    }

    route("/mappings") {
      post {
        val mapping = call.receive<BonvoyageTravelManagerUserMappingInsert>()
        call.respond(api.addTravelManagerUserMapping(mapping))
      }

      delete("/{id}") {
        val id = call.pathUuid("id")
        api.removeTravelManagerUserMapping(id)
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }
}

fun Route.bonvoyageUserRoutes(api: BonvoyageUserApi) {
  route("/bonvoyage/managers") {
    get {
      val user = call.user()
      call.respond(api.listTravelManagers(user.id))
    }
  }

  route("/bonvoyage/trips") {
    get { call.respond(api.listTrips(call.user().id)) }

    route("/{id}") {
      get {
        val id = call.pathUuid("id")
        val trip = api.getTripAggregate(id, call.user().id)
        call.respond(trip)
      }

      get("/welcome-message") {
        val id = call.pathUuid("id")
        val welcomeMessage =
          api.getTripWelcomeMessage(id)
            ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip welcome message not found")
        call.respond(welcomeMessage)
      }

      patch {
        val tripId = call.pathUuid("id")
        val update = call.receive<BonvoyageTripPropertiesUpdate>()
        val trip = api.updateTrip(tripId, call.user().id, update)
        call.respond(trip)
      }

      patch("/reminders") {
        val tripId = call.pathUuid("id")
        val update = call.receive<BonvoyageTripUpdateReminders>()
        val trip = api.updateTripReminders(tripId, call.user().id, update)
        call.respond(trip)
      }

      route("/expenses") {
        get {
          val tripId = call.pathUuid("id")
          val expenses = api.listTripExpenses(tripId, call.user().id)
          call.respond(expenses)
        }

        post {
          val tripId = call.pathUuid("id")
          val form = call.receiveMultipart()

          var image: ByteArray? = null
          var description: String? = null

          form.forEachPart { part ->
            when (part.name) {
              "image" -> {
                val part =
                  part as? PartData.FileItem
                    ?: throw AppError.api(ErrorReason.InvalidParameter, "Invalid image")
                image = part.provider().readRemaining().readByteArray()
              }
              "description" -> {
                val part =
                  part as? PartData.FormItem
                    ?: throw AppError.api(ErrorReason.InvalidParameter, "Invalid description")
                description = part.value
              }
              else ->
                throw AppError.api(ErrorReason.InvalidParameter, "Invalid form field ${part.name}")
            }
          }

          if (image == null) {
            throw AppError.api(ErrorReason.InvalidParameter, "Missing image")
          }

          val attachment =
            IncomingMessageAttachment.Image(
              IncomingImageData.Raw("data:image/jpeg;base64,${image.encodeBase64()}")
            )
          val expense = api.uploadExpense(tripId, call.user(), attachment, description)

          call.respond(expense)
        }

        patch("/{expenseId}") {
          val tripId = call.pathUuid("id")
          val expenseId = call.pathUuid("expenseId")
          val update = call.receive<BonvoyageTravelExpenseUpdateProperties>()
          val expense = api.updateExpense(tripId, call.user().id, expenseId, update)
          call.respond(expense)
        }

        delete("/{expenseId}") {
          val tripId = call.pathUuid("id")
          val expenseId = call.pathUuid("expenseId")
          api.deleteTripExpense(tripId, call.user().id, expenseId)
          call.respond(HttpStatusCode.NoContent)
        }
      }

      post("/report") {
        val tripId = call.pathUuid("id")
        api.generateAndSendReport(tripId, call.user().id, call.user().email)
        call.respond(HttpStatusCode.NoContent)
      }
    }

    route("/requests") {
      get {
        val status = call.queryParam("status")?.let { BonvoyageTravelRequestStatus.valueOf(it) }
        call.respond(api.listTravelRequests(call.user().id, status))
      }

      get("/{id}") {
        val id = call.pathUuid("id")
        val request = api.getTravelRequest(id, call.user().id)
        call.respond(request)
      }

      post {
        val request = call.receive<TravelRequest>()
        val user = call.user()
        val travelRequest = api.requestTravelOrder(user, request)
        call.respond(travelRequest)
      }
    }
  }
}
