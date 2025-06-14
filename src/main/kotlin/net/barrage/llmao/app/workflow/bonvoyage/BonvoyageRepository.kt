package net.barrage.llmao.app.workflow.bonvoyage

import database.postgres.PostgresAtomic
import database.postgres.set
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.repository.MessageRepository
import net.barrage.llmao.core.types.KLocalDate
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KOffsetTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_EXPENSES
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_MANAGERS
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_REQUESTS
import net.barrage.llmao.tables.references.BONVOYAGE_TRIPS
import net.barrage.llmao.tables.references.BONVOYAGE_TRIP_WELCOME_MESSAGES
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine

class BonvoyageRepository(override val context: DSLContext) : PostgresAtomic, MessageRepository {
  suspend fun insertTravelManager(
    userId: String,
    username: String,
    email: String,
  ): BonvoyageTravelManager {
    return context
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
    context
      .deleteFrom(BONVOYAGE_TRAVEL_MANAGERS)
      .where(BONVOYAGE_TRAVEL_MANAGERS.USER_ID.eq(userId))
      .awaitSingle()
  }

  suspend fun listTravelManagers(): List<BonvoyageTravelManagerUserMappingAggregate> {
    val managerMap = mutableMapOf<String, BonvoyageTravelManagerUserMappingAggregate>()

    context
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

    context
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
    return context
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
    context
      .deleteFrom(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS)
      .where(BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS.ID.eq(id))
      .awaitSingle()
  }

  suspend fun insertTravelRequest(
    user: User,
    request: TravelRequestParameters,
  ): BonvoyageTravelRequest {
    return context
      .insertInto(BONVOYAGE_TRAVEL_REQUESTS)
      .set(BONVOYAGE_TRAVEL_REQUESTS.USER_ID, user.id)
      .set(BONVOYAGE_TRAVEL_REQUESTS.USER_FULL_NAME, user.username)
      .set(BONVOYAGE_TRAVEL_REQUESTS.USER_EMAIL, user.email)
      .set(BONVOYAGE_TRAVEL_REQUESTS.START_LOCATION, request.startLocation)
      .set(BONVOYAGE_TRAVEL_REQUESTS.STOPS, request.stops.joinToString("|"))
      .set(BONVOYAGE_TRAVEL_REQUESTS.END_LOCATION, request.endLocation)
      .set(BONVOYAGE_TRAVEL_REQUESTS.TRANSPORT_TYPE, request.transportType.name)
      .set(BONVOYAGE_TRAVEL_REQUESTS.DESCRIPTION, request.description)
      .set(BONVOYAGE_TRAVEL_REQUESTS.START_DATE, request.startDate)
      .set(BONVOYAGE_TRAVEL_REQUESTS.END_DATE, request.endDate)
      .set(BONVOYAGE_TRAVEL_REQUESTS.EXPECTED_START_TIME, request.expectedStartTime)
      .set(BONVOYAGE_TRAVEL_REQUESTS.EXPECTED_END_TIME, request.expectedEndTime)
      .set(BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_TYPE, request.vehicleType)
      .set(BONVOYAGE_TRAVEL_REQUESTS.VEHICLE_REGISTRATION, request.vehicleRegistration)
      .set(BONVOYAGE_TRAVEL_REQUESTS.IS_DRIVER, request.isDriver)
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
    return context
      .selectFrom(BONVOYAGE_TRAVEL_REQUESTS)
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
    return context
      .selectFrom(BONVOYAGE_TRAVEL_REQUESTS)
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
    return context
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
    context
      .update(BONVOYAGE_TRAVEL_REQUESTS)
      .set(BONVOYAGE_TRAVEL_REQUESTS.TRIP_ID, workflowId)
      .where(BONVOYAGE_TRAVEL_REQUESTS.ID.eq(id))
      .awaitSingle()
  }

  suspend fun getTripChatMessages(tripId: KUUID): List<MessageGroupAggregate> {
    val trip = getTrip(tripId)
    val messages = getWorkflowMessages(trip.id)
    val expenseMessageIds =
      context
        .select(BONVOYAGE_TRAVEL_EXPENSES.MESSAGE_GROUP_ID)
        .from(BONVOYAGE_TRAVEL_EXPENSES)
        .where(BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID.eq(tripId))
        .asFlow()
        .map { it.value1()!! }
        .toList()
    return messages.items.filter { it.group.id !in expenseMessageIds }
  }

  suspend fun insertTrip(trip: TripInsert): BonvoyageTrip {
    return context
      .insertInto(BONVOYAGE_TRIPS)
      .set(BONVOYAGE_TRIPS.USER_ID, trip.traveler.userId)
      .set(BONVOYAGE_TRIPS.USER_FULL_NAME, trip.traveler.userFullName)
      .set(BONVOYAGE_TRIPS.USER_EMAIL, trip.traveler.userEmail)
      .set(BONVOYAGE_TRIPS.CREATED_BY_ID, trip.creatingUser.userId)
      .set(BONVOYAGE_TRIPS.CREATED_BY_USERNAME, trip.creatingUser.userFullName)
      .set(BONVOYAGE_TRIPS.CREATED_BY_EMAIL, trip.creatingUser.userEmail)
      .set(BONVOYAGE_TRIPS.TRAVEL_ORDER_ID, trip.travelOrderId)
      .set(BONVOYAGE_TRIPS.START_LOCATION, trip.params.startLocation)
      .set(BONVOYAGE_TRIPS.STOPS, trip.params.stops.joinToString("|"))
      .set(BONVOYAGE_TRIPS.END_LOCATION, trip.params.endLocation)
      .set(BONVOYAGE_TRIPS.START_DATE, trip.params.startDate)
      .set(BONVOYAGE_TRIPS.END_DATE, trip.params.endDate)
      .set(BONVOYAGE_TRIPS.TRANSPORT_TYPE, trip.params.transportType.name)
      .set(BONVOYAGE_TRIPS.DESCRIPTION, trip.params.description)
      .set(BONVOYAGE_TRIPS.START_REMINDER_TIME, trip.params.expectedStartTime)
      .set(BONVOYAGE_TRIPS.END_REMINDER_TIME, trip.params.expectedEndTime)
      .set(BONVOYAGE_TRIPS.VEHICLE_TYPE, trip.params.vehicleType)
      .set(BONVOYAGE_TRIPS.VEHICLE_REGISTRATION, trip.params.vehicleRegistration)
      .set(BONVOYAGE_TRIPS.IS_DRIVER, trip.params.isDriver)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIPS)
      .toTrip()
  }

  suspend fun markTripNotificationAsSent(tripId: KUUID, type: BonvoyageTripNotificationType) {
    val field =
      when (type) {
        BonvoyageTripNotificationType.START_OF_TRIP -> BONVOYAGE_TRIPS.START_REMINDER_SENT_AT
        BonvoyageTripNotificationType.END_OF_TRIP -> BONVOYAGE_TRIPS.END_REMINDER_SENT_AT
      }
    context
      .update(BONVOYAGE_TRIPS)
      .set(field, KOffsetDateTime.now())
      .where(BONVOYAGE_TRIPS.ID.eq(tripId))
      .awaitSingle()
  }

  suspend fun listPendingStartNotifications(
    tripId: KUUID? = null
  ): List<BonvoyageTripNotification> {
    return context
      .select(
        BONVOYAGE_TRIPS.ID,
        BONVOYAGE_TRIPS.USER_EMAIL,
        BONVOYAGE_TRIPS.START_LOCATION,
        BONVOYAGE_TRIPS.END_LOCATION,
        BONVOYAGE_TRIPS.START_DATE,
        BONVOYAGE_TRIPS.START_REMINDER_TIME,
      )
      .from(BONVOYAGE_TRIPS)
      .where(
        BONVOYAGE_TRIPS.START_TIME.isNull
          .and(BONVOYAGE_TRIPS.START_REMINDER_SENT_AT.isNull)
          .and(BONVOYAGE_TRIPS.START_REMINDER_TIME.isNotNull)
          // Exclude everything in the future from the query
          .and(BONVOYAGE_TRIPS.START_DATE.lessOrEqual(KLocalDate.now()))
          .and(BONVOYAGE_TRIPS.START_REMINDER_TIME.lessOrEqual(KOffsetTime.now()))
          .and(tripId?.let { BONVOYAGE_TRIPS.ID.eq(tripId) } ?: DSL.noCondition())
      )
      .asFlow()
      .map {
        val scheduledTime =
          KOffsetDateTime(
            it.get<KLocalDate>(BONVOYAGE_TRIPS.START_DATE),
            it.get<KOffsetTime>(BONVOYAGE_TRIPS.START_REMINDER_TIME),
          )
        BonvoyageTripNotification(
          userEmail = it.get<String>(BONVOYAGE_TRIPS.USER_EMAIL),
          tripId = it.get<KUUID>(BONVOYAGE_TRIPS.ID),
          notificationType = BonvoyageTripNotificationType.START_OF_TRIP,
          scheduledTime = scheduledTime,
          startLocation = it.get<String>(BONVOYAGE_TRIPS.START_LOCATION),
          endLocation = it.get<String>(BONVOYAGE_TRIPS.END_LOCATION),
          sentAt = null,
        )
      }
      .toList()
  }

  suspend fun listPendingEndNotifications(tripId: KUUID? = null): List<BonvoyageTripNotification> {
    return context
      .select(
        BONVOYAGE_TRIPS.USER_EMAIL,
        BONVOYAGE_TRIPS.ID,
        BONVOYAGE_TRIPS.START_LOCATION,
        BONVOYAGE_TRIPS.END_LOCATION,
        BONVOYAGE_TRIPS.END_DATE,
        BONVOYAGE_TRIPS.END_REMINDER_TIME,
      )
      .from(BONVOYAGE_TRIPS)
      .where(
        BONVOYAGE_TRIPS.END_TIME.isNull
          .and(BONVOYAGE_TRIPS.END_REMINDER_SENT_AT.isNull)
          .and(BONVOYAGE_TRIPS.END_REMINDER_TIME.isNotNull)
          // Exclude everything in the future from the query
          .and(BONVOYAGE_TRIPS.END_DATE.lessOrEqual(KLocalDate.now()))
          .and(BONVOYAGE_TRIPS.END_REMINDER_TIME.lessOrEqual(KOffsetTime.now()))
          .and(tripId?.let { BONVOYAGE_TRIPS.ID.eq(tripId) } ?: DSL.noCondition())
      )
      .asFlow()
      .map {
        val scheduledTime =
          KOffsetDateTime(
            it.get<KLocalDate>(BONVOYAGE_TRIPS.END_DATE),
            it.get<KOffsetTime>(BONVOYAGE_TRIPS.END_REMINDER_TIME),
          )
        BonvoyageTripNotification(
          userEmail = it.get<String>(BONVOYAGE_TRIPS.USER_EMAIL),
          tripId = it.get<KUUID>(BONVOYAGE_TRIPS.ID),
          notificationType = BonvoyageTripNotificationType.END_OF_TRIP,
          scheduledTime = scheduledTime,
          startLocation = it.get<String>(BONVOYAGE_TRIPS.START_LOCATION),
          endLocation = it.get<String>(BONVOYAGE_TRIPS.END_LOCATION),
          sentAt = null,
        )
      }
      .toList()
  }

  suspend fun getStartAndEndTripReminders(
    tripId: KUUID
  ): Pair<BonvoyageTripNotification?, BonvoyageTripNotification?> {
    return context
      .select(
        BONVOYAGE_TRIPS.ID,
        BONVOYAGE_TRIPS.USER_EMAIL,
        BONVOYAGE_TRIPS.START_LOCATION,
        BONVOYAGE_TRIPS.END_LOCATION,
        BONVOYAGE_TRIPS.START_DATE,
        BONVOYAGE_TRIPS.START_REMINDER_TIME,
        BONVOYAGE_TRIPS.START_REMINDER_SENT_AT,
        BONVOYAGE_TRIPS.END_DATE,
        BONVOYAGE_TRIPS.END_REMINDER_TIME,
        BONVOYAGE_TRIPS.END_REMINDER_SENT_AT,
      )
      .from(BONVOYAGE_TRIPS)
      .where(BONVOYAGE_TRIPS.ID.eq(tripId))
      .awaitFirstOrNull()
      ?.let {
        val startNotification =
          it.get<KOffsetTime?>(BONVOYAGE_TRIPS.START_REMINDER_TIME)?.let { expectedTime ->
            BonvoyageTripNotification(
              userEmail = it.get<String>(BONVOYAGE_TRIPS.USER_EMAIL),
              tripId = it.get<KUUID>(BONVOYAGE_TRIPS.ID),
              notificationType = BonvoyageTripNotificationType.START_OF_TRIP,
              scheduledTime =
                KOffsetDateTime(it.get<KLocalDate>(BONVOYAGE_TRIPS.START_DATE), expectedTime),
              startLocation = it.get<String>(BONVOYAGE_TRIPS.START_LOCATION),
              endLocation = it.get<String>(BONVOYAGE_TRIPS.END_LOCATION),
              sentAt = it.get<KOffsetDateTime?>(BONVOYAGE_TRIPS.START_REMINDER_SENT_AT),
            )
          }
        val endNotification =
          it.get<KOffsetTime?>(BONVOYAGE_TRIPS.END_REMINDER_TIME)?.let { expectedTime ->
            BonvoyageTripNotification(
              userEmail = it.get<String>(BONVOYAGE_TRIPS.USER_EMAIL),
              tripId = it.get<KUUID>(BONVOYAGE_TRIPS.ID),
              notificationType = BonvoyageTripNotificationType.END_OF_TRIP,
              scheduledTime =
                KOffsetDateTime(it.get<KLocalDate>(BONVOYAGE_TRIPS.END_DATE), expectedTime),
              startLocation = it.get<String>(BONVOYAGE_TRIPS.END_LOCATION),
              endLocation = it.get<String>(BONVOYAGE_TRIPS.END_LOCATION),
              sentAt = it.get<KOffsetDateTime?>(BONVOYAGE_TRIPS.END_REMINDER_SENT_AT),
            )
          }
        Pair(startNotification, endNotification)
      } ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
  }

  suspend fun insertTripWelcomeMessage(
    tripId: KUUID,
    content: String,
  ): BonvoyageTripWelcomeMessage {
    return context
      .insertInto(BONVOYAGE_TRIP_WELCOME_MESSAGES)
      .set(BONVOYAGE_TRIP_WELCOME_MESSAGES.TRIP_ID, tripId)
      .set(BONVOYAGE_TRIP_WELCOME_MESSAGES.CONTENT, content)
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIP_WELCOME_MESSAGES)
      .toTripWelcomeMessage()
  }

  suspend fun getTripWelcomeMessage(tripId: KUUID): BonvoyageTripWelcomeMessage? {
    return context
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

  suspend fun updateTrip(id: KUUID, update: TripPropertiesUpdate): BonvoyageTrip {
    return context
      .update(BONVOYAGE_TRIPS)
      .set(update.startTime, BONVOYAGE_TRIPS.START_TIME)
      .set(update.endDate, BONVOYAGE_TRIPS.END_DATE)
      .set(update.endTime, BONVOYAGE_TRIPS.END_TIME)
      .set(update.startMileage, BONVOYAGE_TRIPS.START_MILEAGE)
      .set(update.endMileage, BONVOYAGE_TRIPS.END_MILEAGE)
      .set(update.vehicleType, BONVOYAGE_TRIPS.VEHICLE_TYPE)
      .set(update.vehicleRegistration, BONVOYAGE_TRIPS.VEHICLE_REGISTRATION)
      .set(update.description, BONVOYAGE_TRIPS.DESCRIPTION)
      .where(BONVOYAGE_TRIPS.ID.eq(id))
      .returning()
      .awaitSingle()
      .into(BONVOYAGE_TRIPS)
      .toTrip()
  }

  suspend fun updateTripReminders(id: KUUID, update: TripUpdateReminders) {
    context
      .update(BONVOYAGE_TRIPS)
      .set(update.expectedStartTime, BONVOYAGE_TRIPS.START_REMINDER_TIME)
      .set(update.expectedEndTime, BONVOYAGE_TRIPS.END_REMINDER_TIME)
      .where(BONVOYAGE_TRIPS.ID.eq(id))
      .awaitSingle()
  }

  suspend fun isTripOwner(id: KUUID, userId: String): Boolean {
    return context
      .selectOne()
      .from(BONVOYAGE_TRIPS)
      .where(BONVOYAGE_TRIPS.ID.eq(id).and(BONVOYAGE_TRIPS.USER_ID.eq(userId)))
      .awaitFirstOrNull() != null
  }

  suspend fun listTrips(userId: String? = null): List<BonvoyageTrip> {
    return context
      .selectFrom(BONVOYAGE_TRIPS)
      .where((userId?.let { BONVOYAGE_TRIPS.USER_ID.eq(userId) } ?: DSL.noCondition()))
      .asFlow()
      .map { it.into(BONVOYAGE_TRIPS).toTrip() }
      .toList()
  }

  suspend fun getTrip(id: KUUID, userId: String? = null): BonvoyageTrip {
    return context
      .selectFrom(BONVOYAGE_TRIPS)
      .where(
        BONVOYAGE_TRIPS.ID.eq(id)
          .and(userId?.let { BONVOYAGE_TRIPS.USER_ID.eq(userId) } ?: DSL.noCondition())
      )
      .awaitFirstOrNull()
      ?.into(BONVOYAGE_TRIPS)
      ?.toTrip() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")
  }

  suspend fun getTripAggregate(id: KUUID, userId: String? = null): BonvoyageTripExpenseAggregate {
    val trip =
      context
        .selectFrom(BONVOYAGE_TRIPS)
        .where(
          BONVOYAGE_TRIPS.ID.eq(id)
            .and(userId?.let { BONVOYAGE_TRIPS.USER_ID.eq(userId) } ?: DSL.noCondition())
        )
        .awaitFirstOrNull()
        ?.into(BONVOYAGE_TRIPS)
        ?.toTrip() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip not found")

    val expenses =
      context
        .select(
          BONVOYAGE_TRAVEL_EXPENSES.ID,
          BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID,
          BONVOYAGE_TRAVEL_EXPENSES.MESSAGE_GROUP_ID,
          BONVOYAGE_TRAVEL_EXPENSES.AMOUNT,
          BONVOYAGE_TRAVEL_EXPENSES.CURRENCY,
          BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PATH,
          BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PROVIDER,
          BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION,
          BONVOYAGE_TRAVEL_EXPENSES.EXPENSE_CREATED_AT,
          BONVOYAGE_TRAVEL_EXPENSES.CREATED_AT,
          BONVOYAGE_TRAVEL_EXPENSES.UPDATED_AT,
        )
        .from(BONVOYAGE_TRAVEL_EXPENSES)
        .where(BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID.eq(trip.id))
        .asFlow()
        .map { it.into(BONVOYAGE_TRAVEL_EXPENSES).toTravelExpense() }
        .toList()

    return BonvoyageTripExpenseAggregate(trip, expenses)
  }

  suspend fun listTripExpenses(tripId: KUUID): List<BonvoyageTravelExpense> {
    return context
      .select(
        BONVOYAGE_TRAVEL_EXPENSES.ID,
        BONVOYAGE_TRAVEL_EXPENSES.TRIP_ID,
        BONVOYAGE_TRAVEL_EXPENSES.MESSAGE_GROUP_ID,
        BONVOYAGE_TRAVEL_EXPENSES.AMOUNT,
        BONVOYAGE_TRAVEL_EXPENSES.CURRENCY,
        BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PATH,
        BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PROVIDER,
        BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION,
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
    expense: TravelExpenseInsert,
  ): Pair<MessageGroupAggregate, BonvoyageTravelExpense> {
    val (id, travelExpense) =
      context.transactionCoroutine { ctx ->
        val dsl = ctx.dsl()
        val id = insertWorkflowMessages(workflowId, BONVOYAGE_WORKFLOW_ID, messages, dsl)
        val e = dsl.insertTravelExpense(workflowId, id, expense)
        Pair(id, e)
      }
    val messageGroup = getMessageGroupAggregate(id)!!
    return Pair(messageGroup, travelExpense)
  }

  suspend fun updateExpense(
    expenseId: KUUID,
    update: BonvoyageTravelExpenseUpdateProperties,
  ): BonvoyageTravelExpense {
    return context
      .update(BONVOYAGE_TRAVEL_EXPENSES)
      .set(update.amount, BONVOYAGE_TRAVEL_EXPENSES.AMOUNT)
      .set(update.currency, BONVOYAGE_TRAVEL_EXPENSES.CURRENCY)
      .set(update.description, BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION)
      .set(update.expenseCreatedAt, BONVOYAGE_TRAVEL_EXPENSES.EXPENSE_CREATED_AT)
      .where(BONVOYAGE_TRAVEL_EXPENSES.ID.eq(expenseId))
      .returning()
      .awaitFirstOrNull()
      ?.toTravelExpense() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Expense not found")
  }

  suspend fun deleteTripExpense(expenseId: KUUID) {
    context
      .deleteFrom(BONVOYAGE_TRAVEL_EXPENSES)
      .where(BONVOYAGE_TRAVEL_EXPENSES.ID.eq(expenseId))
      .awaitFirstOrNull()
  }
}

private suspend fun DSLContext.insertTravelExpense(
  workflowId: KUUID,
  messageGroupId: KUUID,
  expense: TravelExpenseInsert,
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
