package net.barrage.llmao.bonvoyage.routes

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import net.barrage.llmao.bonvoyage.ApproveTravelRequest
import net.barrage.llmao.bonvoyage.BonvoyageAdminApi
import net.barrage.llmao.bonvoyage.BonvoyageTravelManager
import net.barrage.llmao.bonvoyage.BonvoyageTravelManagerUserMapping
import net.barrage.llmao.bonvoyage.BonvoyageTravelManagerUserMappingAggregate
import net.barrage.llmao.bonvoyage.BonvoyageTravelRequest
import net.barrage.llmao.bonvoyage.BonvoyageTravelRequestStatus
import net.barrage.llmao.bonvoyage.BonvoyageTrip
import net.barrage.llmao.bonvoyage.BonvoyageTripFullAggregate
import net.barrage.llmao.bonvoyage.BonvoyageUser
import net.barrage.llmao.bonvoyage.BulkInsertTrip
import net.barrage.llmao.bonvoyage.InsertTrip
import net.barrage.llmao.bonvoyage.TravelManagerUserMappingInsert
import net.barrage.llmao.bonvoyage.TravelRequestStatusUpdate
import net.barrage.llmao.bonvoyage.toBonvoyageUser
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.http.pathUuid
import net.barrage.llmao.core.http.queryParam
import net.barrage.llmao.core.http.user
import net.barrage.llmao.core.types.KUUID

fun Route.bonvoyageAdminRoutes(api: BonvoyageAdminApi) {
  route("/bonvoyage/admin") {
    route("/trips") {
      get(listTrips()) { call.respond(api.listTrips()) }

      post(createTrip()) {
        val insert = call.receive<InsertTrip>()
        val trip = api.createTrip(call.user().toBonvoyageUser(), insert)
        call.respond(trip)
      }

      post("/batch", bulkCreateTrips()) {
        val insert = call.receive<BulkInsertTrip>()
        val trips = api.bulkCreateTrips(call.user().toBonvoyageUser(), insert)
        call.respond(trips)
      }

      get("/{id}", getTrip()) {
        val id = call.pathUuid("id")
        val trip = api.getTripAggregate(id)
        call.respond(trip)
      }

      route("/requests") {
        get(listTravelRequests()) {
          val status = call.queryParam("status")?.let { BonvoyageTravelRequestStatus.valueOf(it) }
          call.respond(api.listTravelRequests(status))
        }

        post(updateTravelRequestStatus()) {
          val request = call.receive<TravelRequestStatusUpdate>()
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
              val trip = api.approveTravelRequest(call.user().toBonvoyageUser(), approval)
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
      get(listManagers()) {
        val userId = call.queryParam("userId")
        call.respond(api.listTravelManagers(userId))
      }

      post(createManager()) {
        val add = call.receive<BonvoyageUser>()
        val manager = api.addTravelManager(add.userId, add.userFullName, add.userEmail)
        call.respond(manager)
      }

      delete("/{userId}", deleteManager()) {
        val userId = call.pathParameters["userId"]!!
        api.removeTravelManager(userId)
        call.respond(HttpStatusCode.NoContent)
      }
    }

    route("/mappings") {
      post(createManagerUserMapping()) {
        val mapping = call.receive<TravelManagerUserMappingInsert>()
        call.respond(api.addTravelManagerUserMapping(mapping))
      }

      delete("/{id}", deleteManagerUserMapping()) {
        val id = call.pathUuid("id")
        api.removeTravelManagerUserMapping(id)
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }
}

private fun listTrips(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin/trips")
  description = "List all trips"
  response {
    HttpStatusCode.OK to
      {
        description = "List of all trips"
        body<List<BonvoyageTrip>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving trips"
        body<List<AppError>> {}
      }
  }
}

private fun createTrip(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Create a new trip"
  request { body<InsertTrip> { description = "Trip" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Trip created successfully"
        body<BonvoyageTrip> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating trip"
        body<List<AppError>> {}
      }
  }
}

private fun bulkCreateTrips(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Bulk create trips"
  request { body<BulkInsertTrip> { description = "Bulk insert trip" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Trips created successfully"
        body<List<BonvoyageTrip>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating trips"
        body<List<AppError>> {}
      }
  }
}

private fun getTrip(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Get a trip"
  request {
    pathParameter<KUUID>("id") {
      description = "Trip ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Trip"
        body<BonvoyageTripFullAggregate> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving trip"
        body<List<AppError>> {}
      }
  }
}

private fun listTravelRequests(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "List all travel requests"
  request { queryParameter<String>("status") { description = "Filter by status" } }
  response {
    HttpStatusCode.OK to
      {
        description = "List of all travel requests"
        body<List<BonvoyageTravelRequest>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving travel requests"
        body<List<AppError>> {}
      }
  }
}

private fun updateTravelRequestStatus(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Update travel request status"
  request { body<TravelRequestStatusUpdate> { description = "Travel request status update" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Travel request status updated successfully"
        body<BonvoyageTrip> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating travel request status"
        body<List<AppError>> {}
      }
  }
}

private fun createManagerUserMapping(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Create a new travel manager user mapping"
  request { body<TravelManagerUserMappingInsert> { description = "Travel manager user mapping" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Travel manager user mapping created successfully"
        body<BonvoyageTravelManagerUserMapping> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating travel manager user mapping"
        body<List<AppError>> {}
      }
  }
}

private fun deleteManagerUserMapping(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Delete a travel manager user mapping"
  request {
    pathParameter<KUUID>("id") {
      description = "Travel manager user mapping ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Travel manager user mapping deleted" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting travel manager user mapping"
        body<List<AppError>> {}
      }
  }
}

private fun listManagers(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "List all travel managers"
  request {
    queryParameter<String>("userId") { description = "Filter by managers assigned to user" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "List of all travel managers"
        body<List<BonvoyageTravelManagerUserMappingAggregate>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving travel managers"
        body<List<AppError>> {}
      }
  }
}

private fun createManager(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Create a new travel manager"
  request { body<BonvoyageUser> { description = "Travel manager" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Travel manager created successfully"
        body<BonvoyageTravelManager> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating travel manager"
        body<List<AppError>> {}
      }
  }
}

private fun deleteManager(): RouteConfig.() -> Unit = {
  tags("bonvoyage/admin")
  description = "Delete a travel manager"
  request { pathParameter<String>("userId") { description = "Travel manager user ID" } }
  response {
    HttpStatusCode.NoContent to { description = "Travel manager deleted" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting travel manager"
        body<List<AppError>> {}
      }
  }
}
