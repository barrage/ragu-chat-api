package net.barrage.llmao.app.workflow.bonvoyage

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.barrage.llmao.app.http.pathUuid
import net.barrage.llmao.app.http.queryParam
import net.barrage.llmao.app.http.user
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason

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
              val trip = api.approveTravelRequest(request.id, user.id, request.reviewComment)
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

    get("/active") { call.respond(api.getActiveTrip(call.user().id)) }

    route("/{id}") {
      get {
        val id = call.pathUuid("id")
        val trip = api.getTripAggregate(id, call.user().id)
        call.respond(trip)
      }

      patch {
        val tripId = call.pathUuid("id")
        val update = call.receive<BonvoyageTripUpdate>()
        when (update) {
          is BonvoyageStartTrip -> {
            val id = call.pathUuid("id")
            val start = call.receive<BonvoyageStartTrip>()
            val trip = api.startTrip(id, call.user().id, start)
            call.respond(trip)
          }
          is BonvoyageTripPropertiesUpdate -> {
            val trip = api.updateTripProperties(tripId, call.user().id, update)
            call.respond(trip)
          }
          is BonvoyageEndTrip -> {
            val trip = api.endTrip(tripId, call.user().id, update)
            call.respond(trip)
          }
        }
      }

      get("/expenses") {
        val tripId = call.pathUuid("id")
        val expenses = api.listTripExpenses(tripId, call.user().id)
        call.respond(expenses)
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
