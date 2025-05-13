package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.database.Atomic
import net.barrage.llmao.core.database.insertMessages
import net.barrage.llmao.core.database.set
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.User
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_EXPENSES
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_MANAGERS
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_REQUESTS
import net.barrage.llmao.tables.references.BONVOYAGE_WORKFLOWS
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine

class BonvoyageRepository(override val dslContext: DSLContext) : Atomic {
  suspend fun insertTravelManager(
    userId: String,
    username: String,
    email: String,
  ): BonvoyageTravelManager {
    return dslContext
      .insertInto(BONVOYAGE_TRAVEL_MANAGERS)
      .set(BONVOYAGE_TRAVEL_MANAGERS.USER_ID, userId)
      .set(BONVOYAGE_TRAVEL_MANAGERS.USER_FULL_NAME, username)
      .set(BONVOYAGE_TRAVEL_MANAGERS.USER_EMAIL, email)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRAVEL_MANAGERS)
      .toTravelManager()
  }

  suspend fun deleteTravelManager(userId: String) {
    dslContext
      .deleteFrom(BONVOYAGE_TRAVEL_MANAGERS)
      .where(BONVOYAGE_TRAVEL_MANAGERS.USER_ID.eq(userId))
      .awaitSingle()
  }

  suspend fun listTravelManagers(): List<BonvoyageTravelManagerUserMappingAggregate> {
    val managerMap = mutableMapOf<String, BonvoyageTravelManagerUserMappingAggregate>()

    dslContext
      .select(
        BONVOYAGE_TRAVEL_MANAGERS.USER_ID,
        BONVOYAGE_TRAVEL_MANAGERS.USER_FULL_NAME,
        BONVOYAGE_TRAVEL_MANAGERS.USER_EMAIL,
        BONVOYAGE_TRAVEL_MANAGERS.CREATED_AT,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.ID,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.TRAVEL_MANAGER_ID,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.USER_ID.`as`("managed_user_id"),
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.DELIVERY,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.CREATED_AT.`as`("mapping_created_at"),
      )
      .from(BONVOYAGE_TRAVEL_MANAGERS)
      .leftJoin(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS)
      .on(
        BONVOYAGE_TRAVEL_MANAGERS.USER_ID.eq(
          BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.TRAVEL_MANAGER_ID
        )
      )
      .asFlow()
      .collect {
        val manager =
          BonvoyageTravelManager(
            userId = it.get<String>(BONVOYAGE_TRAVEL_MANAGERS.USER_ID),
            userFullName = it.get<String>(BONVOYAGE_TRAVEL_MANAGERS.USER_FULL_NAME),
            userEmail = it.get<String>(BONVOYAGE_TRAVEL_MANAGERS.USER_EMAIL),
            createdAt = it.get<KOffsetDateTime>(BONVOYAGE_TRAVEL_MANAGERS.CREATED_AT),
          )

        val userMappingId = it.get<KUUID?>(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.ID)

        if (userMappingId == null) {
          managerMap.computeIfAbsent(manager.userId) { _ ->
            BonvoyageTravelManagerUserMappingAggregate(manager, mutableListOf())
          }
          return@collect
        }

        val mapping =
          BonvoyageTravelManagerUserMapping(
            id = it.get<KUUID?>(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.ID),
            travelManagerId =
              it.get<String>(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.TRAVEL_MANAGER_ID),
            userId = it.get("managed_user_id", String::class.java),
            delivery =
              BonvoyageNotificationDelivery.valueOf(
                it.get<String>(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.DELIVERY)
              ),
            createdAt = it.get("mapping_created_at", KOffsetDateTime::class.java),
          )

        managerMap
          .computeIfAbsent(manager.userId) { _ ->
            BonvoyageTravelManagerUserMappingAggregate(manager, mutableListOf())
          }
          .mappings
          .add(mapping)
      }

    return managerMap.values.toList()
  }

  suspend fun listTravelManagers(userId: String): List<BonvoyageTravelManagerUserMappingAggregate> {
    val managerMap = mutableMapOf<String, BonvoyageTravelManagerUserMappingAggregate>()

    dslContext
      .select(
        BONVOYAGE_TRAVEL_MANAGERS.USER_ID,
        BONVOYAGE_TRAVEL_MANAGERS.USER_FULL_NAME,
        BONVOYAGE_TRAVEL_MANAGERS.USER_EMAIL,
        BONVOYAGE_TRAVEL_MANAGERS.CREATED_AT,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.ID,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.TRAVEL_MANAGER_ID,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.USER_ID.`as`("managed_user_id"),
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.DELIVERY,
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.CREATED_AT.`as`("mapping_created_at"),
      )
      .from(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS)
      .leftJoin(BONVOYAGE_TRAVEL_MANAGERS)
      .on(
        BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.TRAVEL_MANAGER_ID.eq(
          BONVOYAGE_TRAVEL_MANAGERS.USER_ID
        )
      )
      .where(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.USER_ID.eq(userId))
      .asFlow()
      .collect {
        val manager =
          BonvoyageTravelManager(
            userId = it.get<String>(BONVOYAGE_TRAVEL_MANAGERS.USER_ID),
            userFullName = it.get<String>(BONVOYAGE_TRAVEL_MANAGERS.USER_FULL_NAME),
            userEmail = it.get<String>(BONVOYAGE_TRAVEL_MANAGERS.USER_EMAIL),
            createdAt = it.get<KOffsetDateTime>(BONVOYAGE_TRAVEL_MANAGERS.CREATED_AT),
          )

        val mapping =
          BonvoyageTravelManagerUserMapping(
            id = it.get<KUUID>(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.ID),
            travelManagerId =
              it.get<String>(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.TRAVEL_MANAGER_ID),
            userId = it.get("managed_user_id", String::class.java),
            delivery =
              BonvoyageNotificationDelivery.valueOf(
                it.get<String>(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.DELIVERY)
              ),
            createdAt = it.get("mapping_created_at", KOffsetDateTime::class.java),
          )

        managerMap
          .computeIfAbsent(manager.userId) { _ ->
            BonvoyageTravelManagerUserMappingAggregate(manager, mutableListOf())
          }
          .mappings
          .add(mapping)
      }

    return managerMap.values.toList()
  }

  suspend fun insertTravelManagerUserMapping(
    travelManagerId: String,
    userId: String,
    delivery: BonvoyageNotificationDelivery,
  ): BonvoyageTravelManagerUserMapping {
    return dslContext
      .insertInto(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS)
      .set(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.TRAVEL_MANAGER_ID, travelManagerId)
      .set(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.USER_ID, userId)
      .set(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.DELIVERY, delivery.name)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS)
      .toTravelManagerUserMapping()
  }

  suspend fun deleteTravelManagerUserMapping(id: KUUID) {
    dslContext
      .deleteFrom(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS)
      .where(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.ID.eq(id))
      .awaitSingle()
  }

  suspend fun insertTravelRequest(user: User, request: TravelRequest): BonvoyageTravelRequest {
    return dslContext
      .insertInto(BONVOYAGE_TRAVEL_REQUESTS)
      .set(BONVOYAGE_TRAVEL_REQUESTS.USER_ID, user.id)
      .set(BONVOYAGE_TRAVEL_REQUESTS.USER_FULL_NAME, user.username)
      .set(BONVOYAGE_TRAVEL_REQUESTS.USER_EMAIL, user.email)
      .set(BONVOYAGE_TRAVEL_REQUESTS.START_LOCATION, request.startLocation)
      .set(BONVOYAGE_TRAVEL_REQUESTS.STOPS, request.stops.joinToString(","))
      .set(BONVOYAGE_TRAVEL_REQUESTS.END_LOCATION, request.endLocation)
      .set(BONVOYAGE_TRAVEL_REQUESTS.TRANSPORT_TYPE, request.transportType.name)
      .set(BONVOYAGE_TRAVEL_REQUESTS.DESCRIPTION, request.description)
      .set(BONVOYAGE_TRAVEL_REQUESTS.START_DATE_TIME, request.startDateTime)
      .set(BONVOYAGE_TRAVEL_REQUESTS.END_DATE_TIME, request.endDateTime)
      .set(BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_TYPE, request.vehicleType)
      .set(BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_REGISTRATION, request.vehicleRegistration)
      .set(BONVOYAGE_TRAVEL_REQUESTS.STATUS, BonvoyageTravelRequestStatus.PENDING.name)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRAVEL_REQUESTS)
      .toTravelRequest()
  }

  suspend fun listTravelRequests(
    userId: String? = null,
    status: BonvoyageTravelRequestStatus? = null,
  ): List<BonvoyageTravelRequest> {
    return dslContext
      .select(
        BONVOYAGE_TRAVEL_REQUESTS.ID,
        BONVOYAGE_TRAVEL_REQUESTS.USER_ID,
        BONVOYAGE_TRAVEL_REQUESTS.USER_FULL_NAME,
        BONVOYAGE_TRAVEL_REQUESTS.USER_EMAIL,
        BONVOYAGE_TRAVEL_REQUESTS.START_LOCATION,
        BONVOYAGE_TRAVEL_REQUESTS.STOPS,
        BONVOYAGE_TRAVEL_REQUESTS.END_LOCATION,
        BONVOYAGE_TRAVEL_REQUESTS.TRANSPORT_TYPE,
        BONVOYAGE_TRAVEL_REQUESTS.DESCRIPTION,
        BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_TYPE,
        BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_REGISTRATION,
        BONVOYAGE_TRAVEL_REQUESTS.START_DATE_TIME,
        BONVOYAGE_TRAVEL_REQUESTS.END_DATE_TIME,
        BONVOYAGE_TRAVEL_REQUESTS.STATUS,
        BONVOYAGE_TRAVEL_REQUESTS.REVIEWER_ID,
        BONVOYAGE_TRAVEL_REQUESTS.REVIEW_COMMENT,
        BONVOYAGE_TRAVEL_REQUESTS.WORKFLOW_ID,
        BONVOYAGE_TRAVEL_REQUESTS.CREATED_AT,
      )
      .from(BONVOYAGE_TRAVEL_REQUESTS)
      .where(
        userId?.let { BONVOYAGE_TRAVEL_REQUESTS.USER_ID.eq(userId) }
          ?: DSL.noCondition()
            .and(
              status?.let { BONVOYAGE_TRAVEL_REQUESTS.STATUS.eq(status.name) } ?: DSL.noCondition()
            )
      )
      .asFlow()
      .map { it.into(BONVOYAGE_TRAVEL_REQUESTS).toTravelRequest() }
      .toList()
  }

  suspend fun getTravelRequest(id: KUUID): BonvoyageTravelRequest {
    return dslContext
      .select(
        BONVOYAGE_TRAVEL_REQUESTS.ID,
        BONVOYAGE_TRAVEL_REQUESTS.USER_ID,
        BONVOYAGE_TRAVEL_REQUESTS.USER_FULL_NAME,
        BONVOYAGE_TRAVEL_REQUESTS.USER_EMAIL,
        BONVOYAGE_TRAVEL_REQUESTS.START_LOCATION,
        BONVOYAGE_TRAVEL_REQUESTS.STOPS,
        BONVOYAGE_TRAVEL_REQUESTS.END_LOCATION,
        BONVOYAGE_TRAVEL_REQUESTS.TRANSPORT_TYPE,
        BONVOYAGE_TRAVEL_REQUESTS.DESCRIPTION,
        BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_TYPE,
        BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_REGISTRATION,
        BONVOYAGE_TRAVEL_REQUESTS.START_DATE_TIME,
        BONVOYAGE_TRAVEL_REQUESTS.END_DATE_TIME,
        BONVOYAGE_TRAVEL_REQUESTS.STATUS,
        BONVOYAGE_TRAVEL_REQUESTS.REVIEWER_ID,
        BONVOYAGE_TRAVEL_REQUESTS.REVIEW_COMMENT,
        BONVOYAGE_TRAVEL_REQUESTS.WORKFLOW_ID,
        BONVOYAGE_TRAVEL_REQUESTS.CREATED_AT,
      )
      .from(BONVOYAGE_TRAVEL_REQUESTS)
      .where(BONVOYAGE_TRAVEL_REQUESTS.ID.eq(id))
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_TRAVEL_REQUESTS)
      ?.toTravelRequest() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Request not found")
  }

  suspend fun updateTravelRequestStatus(
    id: KUUID,
    reviewerId: String,
    status: BonvoyageTravelRequestStatus,
    reviewComment: String? = null,
  ): BonvoyageTravelRequest {
    return dslContext
      .update(BONVOYAGE_TRAVEL_REQUESTS)
      .set(BONVOYAGE_TRAVEL_REQUESTS.REVIEWER_ID, reviewerId)
      .set(BONVOYAGE_TRAVEL_REQUESTS.REVIEW_COMMENT, reviewComment)
      .set(BONVOYAGE_TRAVEL_REQUESTS.STATUS, status.name)
      .where(BONVOYAGE_TRAVEL_REQUESTS.ID.eq(id))
      .returning()
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_TRAVEL_REQUESTS)
      ?.toTravelRequest() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Request not found")
  }

  suspend fun updateTravelRequestWorkflow(id: KUUID, workflowId: KUUID) {
    dslContext
      .update(BONVOYAGE_TRAVEL_REQUESTS)
      .set(BONVOYAGE_TRAVEL_REQUESTS.WORKFLOW_ID, workflowId)
      .where(BONVOYAGE_TRAVEL_REQUESTS.ID.eq(id))
      .awaitSingle()
  }

  suspend fun insertTrip(trip: BonvoyageTripInsert): BonvoyageTrip {
    return dslContext
      .insertInto(BONVOYAGE_WORKFLOWS)
      .set(BONVOYAGE_WORKFLOWS.USER_ID, trip.userId)
      .set(BONVOYAGE_WORKFLOWS.USER_FULL_NAME, trip.userFullName)
      .set(BONVOYAGE_WORKFLOWS.USER_EMAIL, trip.userEmail)
      .set(BONVOYAGE_WORKFLOWS.TRAVEL_ORDER_ID, trip.travelOrderId)
      .set(BONVOYAGE_WORKFLOWS.START_LOCATION, trip.startLocation)
      .set(BONVOYAGE_WORKFLOWS.STOPS, trip.stops.joinToString(","))
      .set(BONVOYAGE_WORKFLOWS.END_LOCATION, trip.endLocation)
      .set(BONVOYAGE_WORKFLOWS.START_DATE_TIME, trip.startDateTime)
      .set(BONVOYAGE_WORKFLOWS.END_DATE_TIME, trip.endDateTime)
      .set(BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE, trip.transportType.name)
      .set(BONVOYAGE_WORKFLOWS.DESCRIPTION, trip.description)
      .set(BONVOYAGE_WORKFLOWS.VEHICLE_TYPE, trip.vehicleType)
      .set(BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION, trip.vehicleRegistration)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_WORKFLOWS)
      .toTrip()
  }

  suspend fun updateTrip(id: KUUID, update: BonvoyageTripUpdate): BonvoyageTrip {
    return dslContext
      .update(BONVOYAGE_WORKFLOWS)
      .set(update.startDateTime, BONVOYAGE_WORKFLOWS.START_DATE_TIME)
      .set(update.endDateTime, BONVOYAGE_WORKFLOWS.END_DATE_TIME)
      .set(update.description, BONVOYAGE_WORKFLOWS.DESCRIPTION)
      .set(update.vehicleType, BONVOYAGE_WORKFLOWS.VEHICLE_TYPE)
      .set(update.vehicleRegistration, BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION)
      .set(update.startMileage, BONVOYAGE_WORKFLOWS.START_MILEAGE)
      .set(update.endMileage, BONVOYAGE_WORKFLOWS.END_MILEAGE)
      .where(BONVOYAGE_WORKFLOWS.ID.eq(id))
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_WORKFLOWS)
      .toTrip()
  }

  suspend fun isTripOwner(id: KUUID, userId: String): Boolean {
    return dslContext
      .selectOne()
      .from(BONVOYAGE_WORKFLOWS)
      .where(BONVOYAGE_WORKFLOWS.ID.eq(id).and(BONVOYAGE_WORKFLOWS.USER_ID.eq(userId)))
      .awaitFirstOrNull() != null
  }

  suspend fun getTrip(id: KUUID): BonvoyageTrip {
    return dslContext
      .select(
        BONVOYAGE_WORKFLOWS.ID,
        BONVOYAGE_WORKFLOWS.USER_ID,
        BONVOYAGE_WORKFLOWS.USER_FULL_NAME,
        BONVOYAGE_WORKFLOWS.TRAVEL_ORDER_ID,
        BONVOYAGE_WORKFLOWS.START_LOCATION,
        BONVOYAGE_WORKFLOWS.STOPS,
        BONVOYAGE_WORKFLOWS.END_LOCATION,
        BONVOYAGE_WORKFLOWS.START_DATE_TIME,
        BONVOYAGE_WORKFLOWS.END_DATE_TIME,
        BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE,
        BONVOYAGE_WORKFLOWS.DESCRIPTION,
        BONVOYAGE_WORKFLOWS.CREATED_AT,
        BONVOYAGE_WORKFLOWS.UPDATED_AT,
        BONVOYAGE_WORKFLOWS.VEHICLE_TYPE,
        BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION,
        BONVOYAGE_WORKFLOWS.START_MILEAGE,
        BONVOYAGE_WORKFLOWS.END_MILEAGE,
      )
      .from(BONVOYAGE_WORKFLOWS)
      .where(BONVOYAGE_WORKFLOWS.ID.eq(id))
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_WORKFLOWS)
      ?.toTrip() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
  }

  suspend fun getTripAggregate(id: KUUID, userId: String? = null): BonvoyageTripAggregate {
    val where = {
      BONVOYAGE_WORKFLOWS.ID.eq(id)
        .and(userId?.let { BONVOYAGE_WORKFLOWS.USER_ID.eq(userId) } ?: DSL.noCondition())
    }

    val trip =
      dslContext
        .select(
          BONVOYAGE_WORKFLOWS.ID,
          BONVOYAGE_WORKFLOWS.USER_ID,
          BONVOYAGE_WORKFLOWS.USER_FULL_NAME,
          BONVOYAGE_WORKFLOWS.TRAVEL_ORDER_ID,
          BONVOYAGE_WORKFLOWS.START_LOCATION,
          BONVOYAGE_WORKFLOWS.STOPS,
          BONVOYAGE_WORKFLOWS.END_LOCATION,
          BONVOYAGE_WORKFLOWS.START_DATE_TIME,
          BONVOYAGE_WORKFLOWS.END_DATE_TIME,
          BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE,
          BONVOYAGE_WORKFLOWS.DESCRIPTION,
          BONVOYAGE_WORKFLOWS.CREATED_AT,
          BONVOYAGE_WORKFLOWS.UPDATED_AT,
          BONVOYAGE_WORKFLOWS.VEHICLE_TYPE,
          BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION,
          BONVOYAGE_WORKFLOWS.START_MILEAGE,
          BONVOYAGE_WORKFLOWS.END_MILEAGE,
        )
        .from(BONVOYAGE_WORKFLOWS)
        .where(where())
        .awaitFirstOrNull()
        ?.into(BONVOYAGE_WORKFLOWS)
        ?.toTrip() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")

    val expenses =
      dslContext
        .select(
          BONVOYAGE_TRAVEL_EXPENSES.ID,
          BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID,
          BONVOYAGE_TRAVEL_EXPENSES.AMOUNT,
          BONVOYAGE_TRAVEL_EXPENSES.CURRENCY,
          BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PATH,
          BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PROVIDER,
          BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION,
          BONVOYAGE_TRAVEL_EXPENSES.VERIFIED,
          BONVOYAGE_TRAVEL_EXPENSES.EXPENSE_CREATED_AT,
          BONVOYAGE_TRAVEL_EXPENSES.CREATED_AT,
          BONVOYAGE_TRAVEL_EXPENSES.UPDATED_AT,
        )
        .from(BONVOYAGE_TRAVEL_EXPENSES)
        .where(BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID.eq(trip.id))
        .asFlow()
        .map { it.into(BONVOYAGE_TRAVEL_EXPENSES).toTravelExpense() }
        .toList()

    return BonvoyageTripAggregate(trip, expenses)
  }

  suspend fun listTrips(userId: String? = null): List<BonvoyageTrip> {
    return dslContext
      .select(
        BONVOYAGE_WORKFLOWS.ID,
        BONVOYAGE_WORKFLOWS.USER_ID,
        BONVOYAGE_WORKFLOWS.USER_FULL_NAME,
        BONVOYAGE_WORKFLOWS.TRAVEL_ORDER_ID,
        BONVOYAGE_WORKFLOWS.START_LOCATION,
        BONVOYAGE_WORKFLOWS.STOPS,
        BONVOYAGE_WORKFLOWS.END_LOCATION,
        BONVOYAGE_WORKFLOWS.START_DATE_TIME,
        BONVOYAGE_WORKFLOWS.END_DATE_TIME,
        BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE,
        BONVOYAGE_WORKFLOWS.DESCRIPTION,
        BONVOYAGE_WORKFLOWS.CREATED_AT,
        BONVOYAGE_WORKFLOWS.UPDATED_AT,
        BONVOYAGE_WORKFLOWS.VEHICLE_TYPE,
        BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION,
        BONVOYAGE_WORKFLOWS.START_MILEAGE,
        BONVOYAGE_WORKFLOWS.END_MILEAGE,
      )
      .from(BONVOYAGE_WORKFLOWS)
      .where(userId?.let { BONVOYAGE_WORKFLOWS.USER_ID.eq(userId) } ?: DSL.noCondition())
      .asFlow()
      .map { it.into(BONVOYAGE_WORKFLOWS).toTrip() }
      .toList()
  }

  suspend fun insertTravelExpense(tripId: KUUID, expense: BonvoyageTravelExpenseInsert) =
    dslContext.insertTravelExpense(tripId, expense)

  suspend fun listTripExpenses(tripId: KUUID): List<BonvoyageTravelExpense> {
    return dslContext
      .select(
        BONVOYAGE_TRAVEL_EXPENSES.ID,
        BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID,
        BONVOYAGE_TRAVEL_EXPENSES.AMOUNT,
        BONVOYAGE_TRAVEL_EXPENSES.CURRENCY,
        BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PATH,
        BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PROVIDER,
        BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION,
        BONVOYAGE_TRAVEL_EXPENSES.VERIFIED,
        BONVOYAGE_TRAVEL_EXPENSES.CREATED_AT,
        BONVOYAGE_TRAVEL_EXPENSES.UPDATED_AT,
      )
      .from(BONVOYAGE_TRAVEL_EXPENSES)
      .where(BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID.eq(tripId))
      .asFlow()
      .map { it.into(BONVOYAGE_TRAVEL_EXPENSES).toTravelExpense() }
      .toList()
  }

  suspend fun insertMessagesWithExpense(
    workflowId: KUUID,
    messages: List<MessageInsert>,
    expense: BonvoyageTravelExpenseInsert,
  ): Pair<KUUID, BonvoyageTravelExpense> =
    dslContext.transactionCoroutine { ctx ->
      Pair(
        ctx.dsl().insertMessages(workflowId, BONVOYAGE_WORKFLOW_ID, messages),
        ctx.dsl().insertTravelExpense(workflowId, expense),
      )
    }

  suspend fun updateExpense(
    expenseId: KUUID,
    update: BonvoyageTravelExpenseUpdateProperties,
  ): BonvoyageTravelExpense {
    return dslContext
      .update(BONVOYAGE_TRAVEL_EXPENSES)
      .set(update.amount, BONVOYAGE_TRAVEL_EXPENSES.AMOUNT)
      .set(update.currency, BONVOYAGE_TRAVEL_EXPENSES.CURRENCY)
      .set(update.description, BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION)
      .set(update.verified, BONVOYAGE_TRAVEL_EXPENSES.VERIFIED)
      .set(update.expenseCreatedAt, BONVOYAGE_TRAVEL_EXPENSES.EXPENSE_CREATED_AT)
      .where(BONVOYAGE_TRAVEL_EXPENSES.ID.eq(expenseId))
      .returning()
      .awaitFirstOrNull()
      ?.toTravelExpense() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Expense not found")
  }
}

private suspend fun DSLContext.insertTravelExpense(
  workflowId: KUUID,
  expense: BonvoyageTravelExpenseInsert,
): BonvoyageTravelExpense {
  return insertInto(BONVOYAGE_TRAVEL_EXPENSES)
    .set(BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID, workflowId)
    .set(BONVOYAGE_TRAVEL_EXPENSES.AMOUNT, expense.amount)
    .set(BONVOYAGE_TRAVEL_EXPENSES.CURRENCY, expense.currency)
    .set(BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PATH, expense.imagePath)
    .set(BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PROVIDER, expense.imageProvider)
    .set(BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION, expense.description)
    .set(BONVOYAGE_TRAVEL_EXPENSES.EXPENSE_CREATED_AT, expense.expenseCreatedAt)
    .returning()
    .awaitSingle()
    .toTravelExpense()
}
