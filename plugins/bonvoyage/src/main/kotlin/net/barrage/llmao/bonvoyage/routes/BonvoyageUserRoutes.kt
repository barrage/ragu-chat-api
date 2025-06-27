package net.barrage.llmao.bonvoyage.routes

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.util.encodeBase64
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import net.barrage.llmao.bonvoyage.BonvoyageTravelExpense
import net.barrage.llmao.bonvoyage.BonvoyageTravelExpenseUpdateProperties
import net.barrage.llmao.bonvoyage.BonvoyageTravelManagerUserMappingAggregate
import net.barrage.llmao.bonvoyage.BonvoyageTravelRequest
import net.barrage.llmao.bonvoyage.BonvoyageTravelRequestStatus
import net.barrage.llmao.bonvoyage.BonvoyageTrip
import net.barrage.llmao.bonvoyage.BonvoyageTripFullAggregate
import net.barrage.llmao.bonvoyage.BonvoyageTripWelcomeMessage
import net.barrage.llmao.bonvoyage.BonvoyageUserApi
import net.barrage.llmao.bonvoyage.TravelRequestParameters
import net.barrage.llmao.bonvoyage.TripPropertiesUpdate
import net.barrage.llmao.bonvoyage.TripUpdateReminders
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.http.pathUuid
import net.barrage.llmao.core.http.queryParam
import net.barrage.llmao.core.http.user
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.types.KUUID

fun Route.bonvoyageUserRoutes(api: BonvoyageUserApi) {
  route("/bonvoyage/managers") {
    get(listAssignedManagers()) {
      val user = call.user()
      call.respond(api.listTravelManagers(user.id))
    }
  }

  route("/bonvoyage/trips") {
    get(listTrips()) { call.respond(api.listTrips(call.user().id)) }

    route("/{id}") {
      get(getTrip()) {
        val id = call.pathUuid("id")
        val trip = api.getTripAggregate(id, call.user().id)
        call.respond(trip)
      }

      get("/welcome-message", getTripWelcomeMessage()) {
        val id = call.pathUuid("id")
        val welcomeMessage =
          api.getTripWelcomeMessage(id)
            ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip welcome message not found")
        call.respond(welcomeMessage)
      }

      patch(updateTrip()) {
        val tripId = call.pathUuid("id")
        val update = call.receive<TripPropertiesUpdate>()
        val trip = api.updateTrip(tripId, call.user().id, update)
        call.respond(trip)
      }

      patch("/reminders", updateTripReminders()) {
        val tripId = call.pathUuid("id")
        val update = call.receive<TripUpdateReminders>()
        val trip = api.updateTripReminders(tripId, call.user().id, update)
        call.respond(trip)
      }

      route("/expenses") {
        get(listTripExpenses()) {
          val tripId = call.pathUuid("id")
          val expenses = api.listTripExpenses(tripId, call.user().id)
          call.respond(expenses)
        }

        post(uploadExpense()) {
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

        patch("/{expenseId}", updateExpense()) {
          val tripId = call.pathUuid("id")
          val expenseId = call.pathUuid("expenseId")
          val update = call.receive<BonvoyageTravelExpenseUpdateProperties>()
          val expense = api.updateExpense(tripId, call.user().id, expenseId, update)
          call.respond(expense)
        }

        delete("/{expenseId}", deleteExpense()) {
          val tripId = call.pathUuid("id")
          val expenseId = call.pathUuid("expenseId")
          api.deleteTripExpense(tripId, call.user().id, expenseId)
          call.respond(HttpStatusCode.NoContent)
        }
      }

      post("/report", generateReport()) {
        val tripId = call.pathUuid("id")
        api.generateAndSendReport(tripId, call.user().id, call.user().email)
        call.respond(HttpStatusCode.NoContent)
      }
    }

    route("/requests") {
      get(listTravelRequests()) {
        val status = call.queryParam("status")?.let { BonvoyageTravelRequestStatus.valueOf(it) }
        call.respond(api.listTravelRequests(call.user().id, status))
      }

      get("/{id}", getTravelRequest()) {
        val id = call.pathUuid("id")
        val request = api.getTravelRequest(id, call.user().id)
        call.respond(request)
      }

      post(submitTravelRequest()) {
        val request = call.receive<TravelRequestParameters>()
        val user = call.user()
        val travelRequest = api.requestTravelOrder(user, request)
        call.respond(travelRequest)
      }
    }
  }
}

private fun submitTravelRequest(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Submit travel request"
  request { body<TravelRequestParameters> { description = "Travel request parameters" } }
  response {
    HttpStatusCode.OK to
      {
        description = "Travel request submitted"
        body<BonvoyageTravelRequest> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while submitting travel request"
        body<List<AppError>> {}
      }
  }
}

private fun listAssignedManagers(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "List assigned managers"
  response {
    HttpStatusCode.OK to
      {
        description = "List of assigned managers"
        body<List<BonvoyageTravelManagerUserMappingAggregate>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving managers"
        body<List<AppError>> {}
      }
  }
}

private fun getTripWelcomeMessage(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Get trip welcome message"
  response {
    HttpStatusCode.OK to
      {
        description = "Trip welcome message"
        body<BonvoyageTripWelcomeMessage> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving trip welcome message"
        body<List<AppError>> {}
      }
  }
}

private fun listTripExpenses(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "List trip expenses"
  response {
    HttpStatusCode.OK to
      {
        description = "List of trip expenses"
        body<List<BonvoyageTravelExpense>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving trip expenses"
        body<List<AppError>> {}
      }
  }
}

private fun uploadExpense(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Upload expense"
  request {
    pathParameter<KUUID>("id") {
      description = "Trip ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }

    multipartBody {
      part<ByteArray>("image") {
        required = true
        mediaTypes = setOf(ContentType.Image.JPEG, ContentType.Image.PNG)
      }
      part<String>("description") { required = false }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Expense uploaded"
        body<Pair<MessageGroupAggregate, BonvoyageTravelExpense>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while uploading expense"
        body<List<AppError>> {}
      }
  }
}

private fun updateExpense(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Update expense"
  request {
    pathParameter<KUUID>("id") {
      description = "Trip ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("expenseId") {
      description = "Expense ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<BonvoyageTravelExpenseUpdateProperties> { description = "Updated expense properties" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Updated expense"
        body<BonvoyageTravelExpense> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating expense"
        body<List<AppError>> {}
      }
  }
}

private fun deleteExpense(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Delete expense"
  request {
    pathParameter<KUUID>("id") {
      description = "Trip ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("expenseId") {
      description = "Expense ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Expense deleted" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting expense"
        body<List<AppError>> {}
      }
  }
}

private fun generateReport(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Generate and send report"
  request {
    pathParameter<KUUID>("id") {
      description = "Trip ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Report generated and sent" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while generating report"
        body<List<AppError>> {}
      }
  }
}

private fun listTrips(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "List trips"
  response {
    HttpStatusCode.OK to
      {
        description = "List of trips"
        body<List<BonvoyageTrip>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving trips"
        body<List<AppError>> {}
      }
  }
}

private fun getTrip(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Get trip"
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

private fun updateTrip(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Update trip"
  request {
    pathParameter<KUUID>("id") {
      description = "Trip ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<TripPropertiesUpdate> { description = "Updated trip properties" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Updated trip"
        body<BonvoyageTrip> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating trip"
        body<List<AppError>> {}
      }
  }
}

private fun updateTripReminders(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Update trip reminders"
  request {
    pathParameter<KUUID>("id") {
      description = "Trip ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<TripUpdateReminders> { description = "Updated trip reminders" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Updated trip"
        body<BonvoyageTrip> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating trip"
        body<List<AppError>> {}
      }
  }
}

private fun listTravelRequests(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "List travel requests"
  response {
    HttpStatusCode.OK to
      {
        description = "List of travel requests"
        body<List<BonvoyageTravelRequest>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving travel requests"
        body<List<AppError>> {}
      }
  }
}

private fun getTravelRequest(): RouteConfig.() -> Unit = {
  tags("bonvoyage")
  description = "Get travel request"
  response {
    HttpStatusCode.OK to
      {
        description = "Travel request"
        body<BonvoyageTravelRequest> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving travel request"
        body<List<AppError>> {}
      }
  }
}
