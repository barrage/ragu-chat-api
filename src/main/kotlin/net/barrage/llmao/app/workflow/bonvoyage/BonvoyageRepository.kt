package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.database.Atomic
import net.barrage.llmao.core.database.getWorkflowMessages
import net.barrage.llmao.core.database.insertMessages
import net.barrage.llmao.core.database.set
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.User
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_EXPENSES
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_MANAGERS
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_REQUESTS
import net.barrage.llmao.tables.references.BONVOYAGE_TRIPS
import net.barrage.llmao.tables.references.BONVOYAGE_TRIP_NOTIFICATIONS
import net.barrage.llmao.tables.references.BONVOYAGE_TRIP_WELCOME_MESSAGES
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
        BONVOYAGE_TRAVEL_REQUESTS.TRIP_ID,
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

  suspend fun getTravelRequest(id: KUUID, userId: String? = null): BonvoyageTravelRequest {
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
        BONVOYAGE_TRAVEL_REQUESTS.TRIP_ID,
        BONVOYAGE_TRAVEL_REQUESTS.CREATED_AT,
      )
      .from(BONVOYAGE_TRAVEL_REQUESTS)
      .where(
        BONVOYAGE_TRAVEL_REQUESTS.ID.eq(id)
          .and(userId?.let { BONVOYAGE_TRAVEL_REQUESTS.USER_ID.eq(userId) } ?: DSL.noCondition())
      )
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_TRAVEL_REQUESTS)
      ?.toTravelRequest()
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Travel request not found")
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
      ?.toTravelRequest()
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Travel request not found")
  }

  suspend fun updateTravelRequestWorkflow(id: KUUID, workflowId: KUUID) {
    dslContext
      .update(BONVOYAGE_TRAVEL_REQUESTS)
      .set(BONVOYAGE_TRAVEL_REQUESTS.TRIP_ID, workflowId)
      .where(BONVOYAGE_TRAVEL_REQUESTS.ID.eq(id))
      .awaitSingle()
  }

  suspend fun getTripChatMessages(tripId: KUUID): List<MessageGroupAggregate> {
    val trip = getTrip(tripId)
    val messages = dslContext.getWorkflowMessages(trip.id)
    val expenseMessageIds =
      dslContext
        .select(BONVOYAGE_TRAVEL_EXPENSES.MESSAGE_GROUP_ID)
        .from(BONVOYAGE_TRAVEL_EXPENSES)
        .where(BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID.eq(tripId))
        .asFlow()
        .map { it.value1()!! }
        .toList()
    return messages.items.filter { it.group.id !in expenseMessageIds }
  }

  suspend fun insertTrip(trip: BonvoyageTripInsert): BonvoyageTrip {
    return dslContext
      .insertInto(BONVOYAGE_TRIPS)
      .set(BONVOYAGE_TRIPS.USER_ID, trip.userId)
      .set(BONVOYAGE_TRIPS.USER_FULL_NAME, trip.userFullName)
      .set(BONVOYAGE_TRIPS.USER_EMAIL, trip.userEmail)
      .set(BONVOYAGE_TRIPS.TRAVEL_ORDER_ID, trip.travelOrderId)
      .set(BONVOYAGE_TRIPS.START_LOCATION, trip.startLocation)
      .set(BONVOYAGE_TRIPS.STOPS, trip.stops.joinToString(","))
      .set(BONVOYAGE_TRIPS.END_LOCATION, trip.endLocation)
      .set(BONVOYAGE_TRIPS.START_DATE_TIME, trip.startDateTime)
      .set(BONVOYAGE_TRIPS.END_DATE_TIME, trip.endDateTime)
      .set(BONVOYAGE_TRIPS.TRANSPORT_TYPE, trip.transportType.name)
      .set(BONVOYAGE_TRIPS.DESCRIPTION, trip.description)
      .set(BONVOYAGE_TRIPS.VEHICLE_TYPE, trip.vehicleType)
      .set(BONVOYAGE_TRIPS.VEHICLE_REGISTRATION, trip.vehicleRegistration)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIPS)
      .toTrip()
  }

  suspend fun insertTripNotification(
    tripId: KUUID,
    notificationType: BonvoyageTripNotificationType,
  ): BonvoyageTripNotification {
    return dslContext
      .insertInto(BONVOYAGE_TRIP_NOTIFICATIONS)
      .set(BONVOYAGE_TRIP_NOTIFICATIONS.TRIP_ID, tripId)
      .set(BONVOYAGE_TRIP_NOTIFICATIONS.NOTIFICATION_TYPE, notificationType.name)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIP_NOTIFICATIONS)
      .toTripNotification()
  }

  suspend fun listPendingTripNotifications(
    notIn: List<KUUID>? = null
  ): List<BonvoyageTripNotification> {
    return dslContext
      .select(
        BONVOYAGE_TRIP_NOTIFICATIONS.ID,
        BONVOYAGE_TRIP_NOTIFICATIONS.TRIP_ID,
        BONVOYAGE_TRIP_NOTIFICATIONS.NOTIFICATION_TYPE,
        BONVOYAGE_TRIP_NOTIFICATIONS.SENT_AT,
      )
      .from(BONVOYAGE_TRIP_NOTIFICATIONS)
      .where(BONVOYAGE_TRIP_NOTIFICATIONS.SENT_AT.isNull)
      .and(notIn?.let { BONVOYAGE_TRIP_NOTIFICATIONS.ID.notIn(it) } ?: DSL.noCondition())
      .asFlow()
      .map { it.into(BONVOYAGE_TRIP_NOTIFICATIONS).toTripNotification() }
      .toList()
  }

  suspend fun markTripNotificationAsSent(id: KUUID) {
    dslContext
      .update(BONVOYAGE_TRIP_NOTIFICATIONS)
      .set(BONVOYAGE_TRIP_NOTIFICATIONS.SENT_AT, KOffsetDateTime.now())
      .where(BONVOYAGE_TRIP_NOTIFICATIONS.ID.eq(id))
      .awaitSingle()
  }

  suspend fun insertTripWelcomeMessage(
    tripId: KUUID,
    content: String,
  ): BonvoyageTripWelcomeMessage {
    return dslContext
      .insertInto(BONVOYAGE_TRIP_WELCOME_MESSAGES)
      .set(BONVOYAGE_TRIP_WELCOME_MESSAGES.TRIP_ID, tripId)
      .set(BONVOYAGE_TRIP_WELCOME_MESSAGES.CONTENT, content)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIP_WELCOME_MESSAGES)
      .toTripWelcomeMessage()
  }

  suspend fun getTripWelcomeMessage(tripId: KUUID): BonvoyageTripWelcomeMessage? {
    return dslContext
      .select(
        BONVOYAGE_TRIP_WELCOME_MESSAGES.ID,
        BONVOYAGE_TRIP_WELCOME_MESSAGES.TRIP_ID,
        BONVOYAGE_TRIP_WELCOME_MESSAGES.CONTENT,
      )
      .from(BONVOYAGE_TRIP_WELCOME_MESSAGES)
      .where(BONVOYAGE_TRIP_WELCOME_MESSAGES.TRIP_ID.eq(tripId))
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_TRIP_WELCOME_MESSAGES)
      ?.toTripWelcomeMessage()
  }

  suspend fun getActiveTrip(userId: String): BonvoyageTrip? {
    return dslContext
      .select(
        BONVOYAGE_TRIPS.ID,
        BONVOYAGE_TRIPS.USER_ID,
        BONVOYAGE_TRIPS.USER_FULL_NAME,
        BONVOYAGE_TRIPS.TRAVEL_ORDER_ID,
        BONVOYAGE_TRIPS.START_LOCATION,
        BONVOYAGE_TRIPS.STOPS,
        BONVOYAGE_TRIPS.END_LOCATION,
        BONVOYAGE_TRIPS.START_DATE_TIME,
        BONVOYAGE_TRIPS.ACTUAL_START_DATE_TIME,
        BONVOYAGE_TRIPS.END_DATE_TIME,
        BONVOYAGE_TRIPS.ACTUAL_END_DATE_TIME,
        BONVOYAGE_TRIPS.TRANSPORT_TYPE,
        BONVOYAGE_TRIPS.DESCRIPTION,
        BONVOYAGE_TRIPS.COMPLETED,
        BONVOYAGE_TRIPS.ACTIVE,
        BONVOYAGE_TRIPS.CREATED_AT,
        BONVOYAGE_TRIPS.UPDATED_AT,
        BONVOYAGE_TRIPS.VEHICLE_TYPE,
        BONVOYAGE_TRIPS.VEHICLE_REGISTRATION,
        BONVOYAGE_TRIPS.START_MILEAGE,
        BONVOYAGE_TRIPS.END_MILEAGE,
      )
      .from(BONVOYAGE_TRIPS)
      .where(BONVOYAGE_TRIPS.USER_ID.eq(userId).and(BONVOYAGE_TRIPS.ACTIVE.isTrue))
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_TRIPS)
      ?.toTrip()
  }

  suspend fun hasActiveTrip(userId: String): Boolean {
    return dslContext
      .select(BONVOYAGE_TRIPS.ID)
      .from(BONVOYAGE_TRIPS)
      .where(BONVOYAGE_TRIPS.USER_ID.eq(userId).and(BONVOYAGE_TRIPS.ACTIVE.isTrue))
      .awaitFirstOrNull() != null
  }

  suspend fun updateTripStartParameters(id: KUUID, update: BonvoyageStartTrip): BonvoyageTrip {
    return dslContext
      .update(BONVOYAGE_TRIPS)
      .set(update.actualStartDateTime, BONVOYAGE_TRIPS.ACTUAL_START_DATE_TIME)
      .set(update.startingMileage, BONVOYAGE_TRIPS.START_MILEAGE)
      .set(update.vehicleType, BONVOYAGE_TRIPS.VEHICLE_TYPE)
      .set(update.vehicleRegistration, BONVOYAGE_TRIPS.VEHICLE_REGISTRATION)
      .where(BONVOYAGE_TRIPS.ID.eq(id))
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIPS)
      .toTrip()
  }

  suspend fun updateTrip(id: KUUID, update: BonvoyageTripPropertiesUpdate): BonvoyageTrip {
    return dslContext
      .update(BONVOYAGE_TRIPS)
      .set(update.actualStartDateTime, BONVOYAGE_TRIPS.ACTUAL_START_DATE_TIME)
      .set(update.actualEndDateTime, BONVOYAGE_TRIPS.ACTUAL_END_DATE_TIME)
      .set(update.startMileage, BONVOYAGE_TRIPS.START_MILEAGE)
      .set(update.endMileage, BONVOYAGE_TRIPS.END_MILEAGE)
      .set(update.description, BONVOYAGE_TRIPS.DESCRIPTION)
      .where(BONVOYAGE_TRIPS.ID.eq(id))
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIPS)
      .toTrip()
  }

  suspend fun updateTripEndParameters(id: KUUID, update: BonvoyageEndTrip): BonvoyageTrip {
    return dslContext
      .update(BONVOYAGE_TRIPS)
      .set(update.actualEndDateTime, BONVOYAGE_TRIPS.ACTUAL_END_DATE_TIME)
      .set(update.endMileage, BONVOYAGE_TRIPS.END_MILEAGE)
      .where(BONVOYAGE_TRIPS.ID.eq(id))
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIPS)
      .toTrip()
  }

  suspend fun isTripOwner(id: KUUID, userId: String): Boolean {
    return dslContext
      .selectOne()
      .from(BONVOYAGE_TRIPS)
      .where(BONVOYAGE_TRIPS.ID.eq(id).and(BONVOYAGE_TRIPS.USER_ID.eq(userId)))
      .awaitFirstOrNull() != null
  }

  suspend fun listTrips(userId: String? = null, completed: Boolean? = null): List<BonvoyageTrip> {
    return dslContext
      .select(
        BONVOYAGE_TRIPS.ID,
        BONVOYAGE_TRIPS.USER_ID,
        BONVOYAGE_TRIPS.USER_FULL_NAME,
        BONVOYAGE_TRIPS.USER_EMAIL,
        BONVOYAGE_TRIPS.TRAVEL_ORDER_ID,
        BONVOYAGE_TRIPS.START_LOCATION,
        BONVOYAGE_TRIPS.STOPS,
        BONVOYAGE_TRIPS.END_LOCATION,
        BONVOYAGE_TRIPS.START_DATE_TIME,
        BONVOYAGE_TRIPS.ACTUAL_START_DATE_TIME,
        BONVOYAGE_TRIPS.END_DATE_TIME,
        BONVOYAGE_TRIPS.ACTUAL_END_DATE_TIME,
        BONVOYAGE_TRIPS.TRANSPORT_TYPE,
        BONVOYAGE_TRIPS.DESCRIPTION,
        BONVOYAGE_TRIPS.COMPLETED,
        BONVOYAGE_TRIPS.ACTIVE,
        BONVOYAGE_TRIPS.CREATED_AT,
        BONVOYAGE_TRIPS.UPDATED_AT,
        BONVOYAGE_TRIPS.VEHICLE_TYPE,
        BONVOYAGE_TRIPS.VEHICLE_REGISTRATION,
        BONVOYAGE_TRIPS.START_MILEAGE,
        BONVOYAGE_TRIPS.END_MILEAGE,
      )
      .from(BONVOYAGE_TRIPS)
      .where((userId?.let { BONVOYAGE_TRIPS.USER_ID.eq(userId) } ?: DSL.noCondition()))
      .and(completed?.let { BONVOYAGE_TRIPS.COMPLETED.eq(completed) } ?: DSL.noCondition())
      .asFlow()
      .map { it.into(BONVOYAGE_TRIPS).toTrip() }
      .toList()
  }

  suspend fun getTrip(id: KUUID, userId: String? = null): BonvoyageTrip {
    return dslContext
      .select(
        BONVOYAGE_TRIPS.ID,
        BONVOYAGE_TRIPS.USER_ID,
        BONVOYAGE_TRIPS.USER_FULL_NAME,
        BONVOYAGE_TRIPS.USER_EMAIL,
        BONVOYAGE_TRIPS.TRAVEL_ORDER_ID,
        BONVOYAGE_TRIPS.START_LOCATION,
        BONVOYAGE_TRIPS.STOPS,
        BONVOYAGE_TRIPS.END_LOCATION,
        BONVOYAGE_TRIPS.START_DATE_TIME,
        BONVOYAGE_TRIPS.ACTUAL_START_DATE_TIME,
        BONVOYAGE_TRIPS.END_DATE_TIME,
        BONVOYAGE_TRIPS.ACTUAL_END_DATE_TIME,
        BONVOYAGE_TRIPS.TRANSPORT_TYPE,
        BONVOYAGE_TRIPS.DESCRIPTION,
        BONVOYAGE_TRIPS.COMPLETED,
        BONVOYAGE_TRIPS.ACTIVE,
        BONVOYAGE_TRIPS.CREATED_AT,
        BONVOYAGE_TRIPS.UPDATED_AT,
        BONVOYAGE_TRIPS.VEHICLE_TYPE,
        BONVOYAGE_TRIPS.VEHICLE_REGISTRATION,
        BONVOYAGE_TRIPS.START_MILEAGE,
        BONVOYAGE_TRIPS.END_MILEAGE,
      )
      .from(BONVOYAGE_TRIPS)
      .where(
        BONVOYAGE_TRIPS.ID.eq(id)
          .and(userId?.let { BONVOYAGE_TRIPS.USER_ID.eq(userId) } ?: DSL.noCondition())
      )
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_TRIPS)
      ?.toTrip() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
  }

  suspend fun getTripAggregate(id: KUUID, userId: String? = null): BonvoyageTripAggregate {
    val trip =
      dslContext
        .select(
          BONVOYAGE_TRIPS.ID,
          BONVOYAGE_TRIPS.USER_ID,
          BONVOYAGE_TRIPS.USER_FULL_NAME,
          BONVOYAGE_TRIPS.USER_EMAIL,
          BONVOYAGE_TRIPS.TRAVEL_ORDER_ID,
          BONVOYAGE_TRIPS.START_LOCATION,
          BONVOYAGE_TRIPS.STOPS,
          BONVOYAGE_TRIPS.END_LOCATION,
          BONVOYAGE_TRIPS.START_DATE_TIME,
          BONVOYAGE_TRIPS.ACTUAL_START_DATE_TIME,
          BONVOYAGE_TRIPS.END_DATE_TIME,
          BONVOYAGE_TRIPS.ACTUAL_END_DATE_TIME,
          BONVOYAGE_TRIPS.TRANSPORT_TYPE,
          BONVOYAGE_TRIPS.DESCRIPTION,
          BONVOYAGE_TRIPS.COMPLETED,
          BONVOYAGE_TRIPS.ACTIVE,
          BONVOYAGE_TRIPS.CREATED_AT,
          BONVOYAGE_TRIPS.UPDATED_AT,
          BONVOYAGE_TRIPS.VEHICLE_TYPE,
          BONVOYAGE_TRIPS.VEHICLE_REGISTRATION,
          BONVOYAGE_TRIPS.START_MILEAGE,
          BONVOYAGE_TRIPS.END_MILEAGE,
        )
        .from(BONVOYAGE_TRIPS)
        .where(
          BONVOYAGE_TRIPS.ID.eq(id)
            .and(userId?.let { BONVOYAGE_TRIPS.USER_ID.eq(userId) } ?: DSL.noCondition())
        )
        .awaitFirstOrNull()
        ?.into(BONVOYAGE_TRIPS)
        ?.toTrip() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")

    val expenses =
      dslContext
        .select(
          BONVOYAGE_TRAVEL_EXPENSES.ID,
          BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID,
          BONVOYAGE_TRAVEL_EXPENSES.MESSAGE_GROUP_ID,
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
        .where(BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID.eq(trip.id))
        .asFlow()
        .map { it.into(BONVOYAGE_TRAVEL_EXPENSES).toTravelExpense() }
        .toList()

    return BonvoyageTripAggregate(trip, expenses)
  }

  suspend fun listTripExpenses(tripId: KUUID): List<BonvoyageTravelExpense> {
    return dslContext
      .select(
        BONVOYAGE_TRAVEL_EXPENSES.ID,
        BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID,
        BONVOYAGE_TRAVEL_EXPENSES.MESSAGE_GROUP_ID,
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
      .where(BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID.eq(tripId))
      .asFlow()
      .map { it.into(BONVOYAGE_TRAVEL_EXPENSES).toTravelExpense() }
      .toList()
  }

  suspend fun insertMessagesWithExpense(
    workflowId: KUUID,
    messages: List<MessageInsert>,
    expense: BonvoyageTravelExpenseInsert,
  ): BonvoyageTravelExpense =
    dslContext.transactionCoroutine { ctx ->
      val messageGroupId = ctx.dsl().insertMessages(workflowId, BONVOYAGE_WORKFLOW_ID, messages)
      ctx.dsl().insertTravelExpense(workflowId, messageGroupId, expense)
    }

  suspend fun insertMessages(workflowId: KUUID, messages: List<MessageInsert>) =
    dslContext.insertMessages(workflowId, BONVOYAGE_WORKFLOW_ID, messages)

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
  messageGroupId: KUUID,
  expense: BonvoyageTravelExpenseInsert,
): BonvoyageTravelExpense {
  return insertInto(BONVOYAGE_TRAVEL_EXPENSES)
    .set(BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID, workflowId)
    .set(BONVOYAGE_TRAVEL_EXPENSES.MESSAGE_GROUP_ID, messageGroupId)
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
