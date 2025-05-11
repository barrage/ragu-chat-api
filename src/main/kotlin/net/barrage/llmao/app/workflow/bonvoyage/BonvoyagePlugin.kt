package net.barrage.llmao.app.workflow.bonvoyage

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.barrage.llmao.app.http.pathUuid
import net.barrage.llmao.app.http.queryParam
import net.barrage.llmao.app.http.user
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.WorkflowFactoryManager

internal const val BONVOYAGE_WORKFLOW_ID = "BONVOYAGE"

class TripotronPlugin() : Plugin {
  private lateinit var admin: BonvoyageAdminApi
  private lateinit var traveler: BonvoyageTravelerApi

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override suspend fun configureState(config: ApplicationConfig, state: ApplicationState) {
    admin = BonvoyageAdminApi(BonvoyageRepository(state.database), state.email)
    traveler = BonvoyageTravelerApi(BonvoyageRepository(state.database), state.email)
    BonvoyageWorkflowFactory.init(config, state)
    WorkflowFactoryManager.register(BonvoyageWorkflowFactory)
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    authenticate("admin") {
      route("/bonvoyage/admin") {
        route("/trips") {
          get { call.respond(admin.listTrips()) }

          get("/{id}") {
            val id = call.pathUuid("id")
            val trip = admin.getTripAggregate(id)
            call.respond(trip)
          }

          route("/requests") {
            get {
              val status =
                call.queryParam("status")?.let { BonvoyageTravelRequestStatus.valueOf(it) }
              call.respond(admin.listTravelRequests(status))
            }

            post {
              val request = call.receive<BonvoyageTravelRequestStatusUpdate>()
              val user = call.user()

              when (request.status) {
                BonvoyageTravelRequestStatus.APPROVED -> {
                  val trip = admin.approveTravelRequest(request.id, user.id, request.reviewComment)
                  call.respond(trip)
                }

                BonvoyageTravelRequestStatus.REJECTED -> {
                  admin.rejectTravelRequest(request.id, user.id, request.reviewComment)
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
            call.respond(admin.listTravelManagers(userId))
          }

          post {
            val add = call.receive<BonvoyageTravelManagerInsert>()
            val manager = admin.addTravelManager(add.userId, add.userFullName, add.userEmail)
            call.respond(manager)
          }

          delete("/{userId}") {
            val userId = call.pathParameters["userId"]!!
            admin.removeTravelManager(userId)
            call.respond(HttpStatusCode.NoContent)
          }
        }

        route("/mappings") {
          post {
            val mapping = call.receive<BonvoyageTravelManagerUserMappingInsert>()
            call.respond(admin.addTravelManagerUserMapping(mapping))
          }

          delete("/{id}") {
            val id = call.pathUuid("id")
            admin.removeTravelManagerUserMapping(id)
            call.respond(HttpStatusCode.NoContent)
          }
        }
      }
    }

    authenticate("user") {
      route("/bonvoyage/managers") {
        get {
          val user = call.user()
          call.respond(traveler.listTravelManagers(user.id))
        }
      }

      route("/bonvoyage/trips") {
        get { call.respond(traveler.listTrips(call.user().id)) }

        get("/{id}") {
          val id = call.pathUuid("id")
          val trip = traveler.getTripAggregate(id, call.user().id)
          call.respond(trip)
        }

        route("/requests") {
          get {
            val status = call.queryParam("status")?.let { BonvoyageTravelRequestStatus.valueOf(it) }
            call.respond(traveler.listTravelRequests(call.user().id, status))
          }

          post {
            val request = call.receive<TravelRequest>()
            val user = call.user()
            traveler.requestTravelOrder(user, request)
            call.respond(HttpStatusCode.NoContent)
          }
        }
      }
    }
  }

  override fun RequestValidationConfig.configureRequestValidation() {
    validate<TravelRequest>(TravelRequest::validate)
  }
}
