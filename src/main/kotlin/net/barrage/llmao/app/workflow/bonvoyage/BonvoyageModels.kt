package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.NotBlank
import net.barrage.llmao.core.Range
import net.barrage.llmao.core.ValidEmail
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.tables.records.BonvoyageTravelExpensesRecord
import net.barrage.llmao.tables.records.BonvoyageTravelManagerUserMappingsRecord
import net.barrage.llmao.tables.records.BonvoyageTravelManagersRecord
import net.barrage.llmao.tables.records.BonvoyageTravelRequestsRecord
import net.barrage.llmao.tables.records.BonvoyageTripWelcomeMessagesRecord
import net.barrage.llmao.tables.records.BonvoyageTripsRecord
import net.barrage.llmao.types.KLocalDate
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KOffsetTime
import net.barrage.llmao.types.KUUID

@Serializable
data class BonvoyageTripFullAggregate(
  val trip: BonvoyageTrip,
  val expenses: List<BonvoyageTravelExpense>,
  val welcomeMessage: BonvoyageTripWelcomeMessage?,
  val chatMessages: List<MessageGroupAggregate>,
  val reminders: Pair<BonvoyageTripNotification?, BonvoyageTripNotification?>,
)

@Serializable
data class BonvoyageTripExpenseAggregate(
  val trip: BonvoyageTrip,
  val expenses: List<BonvoyageTravelExpense>,
)

@Serializable
data class BonvoyageTravelManagerUserMappingAggregate(
  val manager: BonvoyageTravelManager,
  val mappings: MutableList<BonvoyageTravelManagerUserMapping>,
)

@Serializable
data class BonvoyageTravelManager(
  val userId: String,
  val userFullName: String,
  val userEmail: String,
  val createdAt: KOffsetDateTime?,
)

@Serializable
data class BonvoyageTravelManagerUserMapping(
  val id: KUUID,
  val travelManagerId: String,
  val userId: String,
  val delivery: BonvoyageNotificationDelivery,
  val createdAt: KOffsetDateTime?,
)

/**
 * Holds the information for a traveler. The user ID is crucial to linking trips to the currently
 * logged in user.
 */
@Serializable
data class BonvoyageUser(
  /** User ID on the auth server. */
  @NotBlank val userId: String,

  /** User's full name. */
  @NotBlank val userFullName: String,

  /** User's email. */
  @NotBlank @ValidEmail val userEmail: String,
) : Validation

internal fun User.toBonvoyageUser() = BonvoyageUser(id, username, email)

/**
 * Represents a request for a travel order that can be approved or rejected by travel managers.
 *
 * This entity is immutable.
 */
@Serializable
data class BonvoyageTravelRequest(
  /** Primary key. */
  val id: KUUID,

  /** Traveler information. */
  val traveler: BonvoyageUser,

  /**
   * Current status of the request.
   *
   * If [BonvoyageTravelRequestStatus.PENDING], the request is pending review and [reviewerId],
   * [reviewComment] and [tripId] are null.
   *
   * If [BonvoyageTravelRequestStatus.APPROVED], a corresponding trip entry will be created and
   * [tripId] will point to it. In this case [reviewerId] is certain to be non-null and indicates
   * which user approved the request.
   *
   * If [BonvoyageTravelRequestStatus.REJECTED], no trip entry will be created and [tripId] will be
   * null.
   *
   * None of the cases above guarantee that [reviewComment] is non-null, however managers are
   * advised to provide a comment when rejecting a request.
   */
  val status: BonvoyageTravelRequestStatus,

  /**
   * Starting location that will be indicated on the travel order if approved. It is guaranteed that
   * this will be the same on the corresponding trip entry.
   */
  val startLocation: String,

  /**
   * Stops that will be indicated on the travel order if approved. It is guaranteed that this will
   * be the same on the corresponding trip entry.
   *
   * See [BonvoyageTrip.stops].
   */
  val stops: List<String>,

  /**
   * Ending location that will be indicated on the travel order if approved. It is guaranteed that
   * this will be the same on the corresponding trip entry.
   */
  val endLocation: String,

  /** Type of transport utilised on the trip. */
  val transportType: TransportType,

  /** The reason for the travel request. */
  val description: String,

  /** The start date of the trip. */
  val startDate: KLocalDate,

  /**
   * The end date of the trip.
   *
   * This may differ from the trip entry's end time due to unforeseen circumstances.
   */
  val endDate: KLocalDate,

  /** User ID of the reviewer (as per the auth server). */
  val reviewerId: String?,

  /** Additional explanation provided by the reviewer. */
  val reviewComment: String?,

  /**
   * Only non-null if the status is [BonvoyageTravelRequestStatus.APPROVED].
   *
   * Points to the corresponding trip entry.
   */
  val tripId: KUUID?,

  /** When the request was created. */
  val createdAt: KOffsetDateTime,

  // Reminder fields

  /** Expected start time of trip, used for reminders. */
  val expectedStartTime: KOffsetTime?,

  /** Expected end time of trip, used for reminders. */
  val expectedEndTime: KOffsetTime?,

  // Personal vehicle fields

  /**
   * If the transport type is [TransportType.PERSONAL], this is the vehicle type (brand and model).
   */
  val vehicleType: String?,

  /**
   * If the transport type is [TransportType.PERSONAL], this is the vehicle's registration plates.
   */
  val vehicleRegistration: String?,

  /** Enables additional field inputs when working with the trip. */
  val isDriver: Boolean,
)

/** The status of a travel request. */
@Serializable
enum class BonvoyageTravelRequestStatus {
  /** Travel request has been created and is pending review. */
  PENDING,

  /** Travel request has been approved and a trip has been created. */
  APPROVED,

  /**
   * Travel request has been rejected and no trip will be created. Usually accompanied by a message.
   */
  REJECTED,
}

/**
 * Used for travel manager user mappings to determine how to notify them when users request travel
 * orders.
 */
@Serializable
enum class BonvoyageNotificationDelivery {
  /** Notify via email. */
  EMAIL,

  /** Notify via push notification. */
  PUSH,
}

@Serializable
enum class TransportType {
  /** The trip is performed with public transport. */
  PUBLIC,

  /**
   * The trip is performed with a personal vehicle. This mandates that start and end mileage be
   * provided along with the vehicle's registration plates.
   */
  PERSONAL,
}

/**
 * A workflow entry with Bonvoyage. A Bonvoyage workflow is a wrapper around a business trip.
 *
 * We always assume these trips are best effort, meaning the correctness of the reported times is
 * not guaranteed and largely depends on the user. We allow users to set these at any time, but have
 * notifications in place to remind them to do so.
 */
@Serializable
data class BonvoyageTrip(
  /** Unique identifier for this workflow. */
  val id: KUUID,

  /** Traveler information. */
  val traveler: BonvoyageUser,

  /** The travel order ID. Without this, trips cannot exist. */
  val travelOrderId: String,

  /** Where the trip started. */
  val startLocation: String,

  /**
   * Trip stops.
   *
   * In cases of trips with no stops, i.e. one way trips to a single destination, this will always
   * be the same as `endLocation`.
   *
   * In cases of return trips to a single destination, this contains the destination, and
   * `endLocation == startLocation`.
   *
   * In any trip with multiple destinations, this contains all the stops in the order they are
   * visited.
   */
  val stops: List<String>,

  /** Where the trip ends. */
  val endLocation: String,

  /** Trip start date. */
  val startDate: KLocalDate,

  /** Trip end date. */
  val endDate: KLocalDate,

  /**
   * The actual time when the trip starts, entered by the user.
   *
   * Modifiable at any time by the user during the trip.
   */
  val startTime: KOffsetTime?,

  /**
   * The actual time when the trip ended, entered by the user.
   *
   * Modifiable at any time by the user during the trip.
   */
  val endTime: KOffsetTime?,

  /** Transportation type, public or personal. */
  val transportType: TransportType,

  /**
   * The purpose of the business trip and any additional information about the trip entered by the
   * user during.
   *
   * Modifiable at any time by the user during the trip.
   */
  val description: String,

  /**
   * When the user finalizes the trip, this is set to the version of the trip that was sent to
   * accounting at that time.
   */
  val versionSent: Int?,

  /** Updated every time a trip's properties are. */
  val version: Int,

  /** When the database entry was created. */
  val createdAt: KOffsetDateTime,

  /** When the database entry was last updated. */
  val updatedAt: KOffsetDateTime,

  /** The user who approved/created the trip. */
  val creatingUser: BonvoyageUser,

  // Fields for reminders

  /** Expected start time of trip, used for reminders. */
  val expectedStartTime: KOffsetTime?,

  /** Expected end time of trip, used for reminders. */
  val expectedEndTime: KOffsetTime?,

  /**
   * Only non-null if the expected start time is not null, has been reached, and the reminder has
   * been sent successfully.
   *
   * Managed by the system and never the user.
   */
  val startReminderSentAt: KOffsetDateTime?,

  /**
   * Only non-null if the expected end time is not null, has been reached, and the reminder has been
   * sent successfully.
   *
   * Managed by the system and never the user.
   */
  val endReminderSentAt: KOffsetDateTime?,

  // Fields for personal vehicle

  /** The brand of the vehicle used, e.g. Ford Focus MK2, a.k.a. The Gentleman's Vehicle. */
  val vehicleType: String?,

  /** Personal vehicle licence plate identifier. */
  val vehicleRegistration: String?,

  /**
   * The mileage of the vehicle when the trip started. Has to be filled in only by drivers.
   * Passengers can always leave this null.
   */
  val startMileage: String?,

  /**
   * The mileage of the vehicle when the trip ended. Has to be filled in only by drivers. Passengers
   * can always leave this null.
   */
  val endMileage: String?,

  /**
   * Whether or not the user is the driver of the vehicle, in case of trips with personal vehicles.
   */
  val isDriver: Boolean,
)

/** A pending notification aggregated from a trip. */
@Serializable
data class BonvoyageTripNotification(
  val userEmail: String,
  val tripId: KUUID,
  val notificationType: BonvoyageTripNotificationType,
  val scheduledTime: KOffsetDateTime,
  val startLocation: String,
  val endLocation: String,

  /** If this is null, the notification is pending. */
  val sentAt: KOffsetDateTime?,
)

@Serializable
enum class BonvoyageTripNotificationType {
  START_OF_TRIP,
  END_OF_TRIP,
}

/**
 * Sent when a travel request is approved and its corresponding trip is created. A friendly welcome
 * message that contains the trip summary. Used for display purposes.
 */
@Serializable
data class BonvoyageTripWelcomeMessage(val id: KUUID, val tripId: KUUID, val content: String)

/**
 * A single expense on a business trip. These are aggregated at the end and presented to the user
 * for review before finalizing the trip report.
 */
@Serializable
data class BonvoyageTravelExpense(
  /** Unique identifier for this expense. */
  val id: KUUID,

  /** The trip this expense belongs to. */
  val tripId: KUUID,

  /**
   * The message group the expense is tied to. Used to filter out expenses when querying chats.
   *
   * If the group is deleted, the expense is deleted.
   */
  val messageGroupId: KUUID,

  /** Amount of money spent on this expense. */
  val amount: Double,

  /** The currency of the expense. */
  val currency: String,

  /** The path to the image presented with the expense. */
  val imagePath: String,

  /** The storage provider for the image. */
  val imageProvider: String,

  /** Expense description. */
  val description: String,

  /** Date time of the expense, as indicated by the receipt. */
  val expenseCreatedAt: KOffsetDateTime,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable
data class BonvoyageTravelExpenseUpdateProperties(
  @Range(min = 0.0) val amount: PropertyUpdate<Double> = PropertyUpdate.Undefined,
  @NotBlank val currency: PropertyUpdate<String> = PropertyUpdate.Undefined,
  @NotBlank val description: PropertyUpdate<String> = PropertyUpdate.Undefined,
  val expenseCreatedAt: PropertyUpdate<KOffsetDateTime> = PropertyUpdate.Undefined,
)

fun BonvoyageTravelManagersRecord.toTravelManager() =
  BonvoyageTravelManager(
    userId = this.userId,
    userFullName = this.userFullName,
    userEmail = this.userEmail,
    createdAt = this.createdAt!!,
  )

fun BonvoyageTravelManagerUserMappingsRecord.toTravelManagerUserMapping() =
  BonvoyageTravelManagerUserMapping(
    id = this.id!!,
    travelManagerId = this.travelManagerId,
    userId = this.userId,
    delivery = BonvoyageNotificationDelivery.valueOf(this.delivery),
    createdAt = this.createdAt!!,
  )

fun BonvoyageTripsRecord.toTrip() =
  BonvoyageTrip(
    id = this.id!!,
    traveler =
      BonvoyageUser(
        userId = this.userId,
        userFullName = this.userFullName,
        userEmail = this.userEmail,
      ),
    travelOrderId = this.travelOrderId,
    startLocation = this.startLocation,
    stops = this.stops.split("|").filter { it.isNotBlank() },
    endLocation = this.endLocation,
    startDate = this.startDate,
    startTime = this.startTime,
    endDate = this.endDate,
    endTime = this.endTime,
    transportType = TransportType.valueOf(this.transportType),
    description = this.description,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    creatingUser =
      BonvoyageUser(
        userId = this.createdById,
        userFullName = this.createdByUsername,
        userEmail = this.createdByEmail,
      ),
    // Reminders
    expectedStartTime = this.startReminderTime,
    expectedEndTime = this.endReminderTime,
    startReminderSentAt = this.startReminderSentAt,
    endReminderSentAt = this.endReminderSentAt,
    version = this.version!!,
    versionSent = this.versionSent,
    // Personal vehicle
    vehicleType = this.vehicleType,
    vehicleRegistration = this.vehicleRegistration,
    startMileage = this.startMileage,
    endMileage = this.endMileage,
    isDriver = this.isDriver!!,
  )

fun BonvoyageTripWelcomeMessagesRecord.toTripWelcomeMessage() =
  BonvoyageTripWelcomeMessage(id = this.id!!, tripId = this.tripId, content = this.content)

fun BonvoyageTravelExpensesRecord.toTravelExpense() =
  BonvoyageTravelExpense(
    id = this.id!!,
    tripId = this.tripId,
    messageGroupId = this.messageGroupId,
    amount = this.amount,
    currency = this.currency,
    imagePath = this.imagePath,
    imageProvider = this.imageProvider,
    description = this.description,
    expenseCreatedAt = this.expenseCreatedAt,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

fun BonvoyageTravelRequestsRecord.toTravelRequest() =
  BonvoyageTravelRequest(
    id = this.id!!,
    traveler =
      BonvoyageUser(
        userId = this.userId,
        userFullName = this.userFullName,
        userEmail = this.userEmail,
      ),
    startLocation = this.startLocation,
    stops = this.stops.split("|").filter { it.isNotBlank() },
    endLocation = this.endLocation,
    transportType = TransportType.valueOf(this.transportType),
    description = this.description,
    startDate = this.startDate,
    endDate = this.endDate,
    expectedStartTime = this.expectedStartTime,
    expectedEndTime = this.expectedEndTime,
    status = BonvoyageTravelRequestStatus.valueOf(this.status),
    reviewerId = this.reviewerId,
    reviewComment = this.reviewComment,
    tripId = this.tripId,
    createdAt = this.createdAt!!,
    vehicleType = this.vehicleType,
    vehicleRegistration = this.vehicleRegistration,
    isDriver = this.isDriver!!,
  )
