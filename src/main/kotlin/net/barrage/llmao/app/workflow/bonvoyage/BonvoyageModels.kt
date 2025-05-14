package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.tables.records.BonvoyageTravelExpensesRecord
import net.barrage.llmao.tables.records.BonvoyageTravelManagerUserMappingsRecord
import net.barrage.llmao.tables.records.BonvoyageTravelManagersRecord
import net.barrage.llmao.tables.records.BonvoyageTravelRequestsRecord
import net.barrage.llmao.tables.records.BonvoyageWorkflowsRecord
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

@Serializable
data class BonvoyageTripAggregate(
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
 * Represents a request for a travel order that can be approved or rejected by travel managers.
 *
 * This entity is immutable.
 */
@Serializable
data class BonvoyageTravelRequest(
  /** Primary key. */
  val id: KUUID,

  /** User ID on the auth server. */
  val userId: String,

  /** Official full name of the user. */
  val userFullName: String,

  /** Email used to send travel reports. */
  val userEmail: String,

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

  /**
   * If the transport type is [TransportType.Personal], this is the vehicle type (brand and model).
   */
  val vehicleType: String?,

  /**
   * If the transport type is [TransportType.Personal], this is the vehicle's registration plates.
   */
  val vehicleRegistration: String?,

  /**
   * The anticipated start of the trip.
   *
   * This may differ from the trip entry's start time due to unforeseen circumstances.
   */
  val startDateTime: KOffsetDateTime,

  /**
   * The anticipated end of the trip.
   *
   * This may differ from the trip entry's end time due to unforeseen circumstances.
   */
  val endDateTime: KOffsetDateTime,

  /**
   * Current status of the request.
   *
   * If [BonvoyageTravelRequestStatus.PENDING], the request is pending review and [reviewerId],
   * [reviewComment] and [workflowId] are null.
   *
   * If [BonvoyageTravelRequestStatus.APPROVED], a corresponding trip entry will be created and
   * [workflowId] will point to it. In this case [reviewerId] is certain to be non-null and
   * indicates which user approved the request.
   *
   * If [BonvoyageTravelRequestStatus.REJECTED], no trip entry will be created and [workflowId] will
   * be null.
   *
   * None of the cases above guarantee that [reviewComment] is non-null, however managers are
   * advised to provide a comment when rejecting a request.
   */
  val status: BonvoyageTravelRequestStatus,

  /** User ID of the reviewer (as per the auth server). */
  val reviewerId: String?,

  /** Additional explanation provided by the reviewer. */
  val reviewComment: String?,

  /**
   * Only non-null if the status is [BonvoyageTravelRequestStatus.APPROVED].
   *
   * Points to the corresponding trip entry.
   */
  val workflowId: KUUID?,

  /** When the request was created. */
  val createdAt: KOffsetDateTime,
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

/** A workflow entry with Bonvoyage. A Bonvoyage workflow is a wrapper around a business trip. */
@Serializable
data class BonvoyageTrip(
  /** Unique identifier for this workflow. */
  val id: KUUID,

  /** The user's ID, for the authorization server. */
  val userId: String,

  /** The full name (first + last) of the user who initiated the trip. */
  val userFullName: String,

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
   * In any trips with multiple destinations, this contains all the stops in the order they are
   * visited.
   */
  val stops: List<String>,

  /** Where the trip ends. */
  val endLocation: String,

  /** When the trip started. */
  val startDateTime: KOffsetDateTime,

  /** When the trip ends. */
  val endDateTime: KOffsetDateTime,

  /** Transportation type, public or personal. */
  val transportType: TransportType,

  /** The purpose of the business trip. */
  val description: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,

  // Optional fields for personal vehicle

  /** The brand of the vehicle used, e.g. Ford Focus MK2, a.k.a. The Gentleman's Vehicle. */
  val vehicleType: String?,

  /** Personal vehicle licence plate identifier. */
  val vehicleRegistration: String?,

  /** The mileage of the vehicle when the trip started. */
  val startMileage: String?,

  /** The mileage of the vehicle when the trip ended. */
  val endMileage: String?,
)

/**
 * A single expense on a business trip. These are aggregated at the end and presented to the user
 * for review before finalizing the trip report.
 */
@Serializable
data class BonvoyageTravelExpense(
  /** Unique identifier for this expense. */
  val id: KUUID,

  /** The trip this expense belongs to. */
  val workflowId: KUUID,

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

  /**
   * Verification status. All expenses must be verified by the end user who sent them since the
   * expense data is auto-generated by LLMs.
   */
  val verified: Boolean,

  /** Date time of the expense, as indicated by the receipt. */
  val expenseCreatedAt: KOffsetDateTime,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable
data class BonvoyageTravelExpenseUpdateProperties(
  val amount: PropertyUpdate<Double> = PropertyUpdate.Undefined,
  val currency: PropertyUpdate<String> = PropertyUpdate.Undefined,
  val description: PropertyUpdate<String> = PropertyUpdate.Undefined,
  val verified: PropertyUpdate<Boolean> = PropertyUpdate.Undefined,
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

fun BonvoyageWorkflowsRecord.toTrip() =
  BonvoyageTrip(
    id = this.id!!,
    userId = this.userId,
    userFullName = this.userFullName,
    travelOrderId = this.travelOrderId,
    startLocation = this.startLocation,
    stops = this.stops.split(","),
    endLocation = this.endLocation,
    startDateTime = this.startDateTime,
    endDateTime = this.endDateTime,
    transportType = TransportType.valueOf(this.transportType),
    description = this.description,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    vehicleType = this.vehicleType,
    vehicleRegistration = this.vehicleRegistration,
    startMileage = this.startMileage,
    endMileage = this.endMileage,
  )

fun BonvoyageTravelExpensesRecord.toTravelExpense() =
  BonvoyageTravelExpense(
    id = this.id!!,
    workflowId = this.workflowId,
    messageGroupId = this.messageGroupId,
    amount = this.amount,
    currency = this.currency,
    imagePath = this.imagePath,
    imageProvider = this.imageProvider,
    description = this.description,
    expenseCreatedAt = this.expenseCreatedAt,
    verified = this.verified == true,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

fun BonvoyageTravelRequestsRecord.toTravelRequest() =
  BonvoyageTravelRequest(
    id = this.id!!,
    userId = this.userId,
    userFullName = this.userFullName,
    userEmail = this.userEmail,
    startLocation = this.startLocation,
    stops = this.stops.split(","),
    endLocation = this.endLocation,
    transportType = TransportType.valueOf(this.transportType),
    description = this.description,
    vehicleType = this.vehicleType,
    vehicleRegistration = this.vehicleRegistration,
    startDateTime = this.startDateTime,
    endDateTime = this.endDateTime,
    status = BonvoyageTravelRequestStatus.valueOf(this.status),
    reviewerId = this.reviewerId,
    reviewComment = this.reviewComment,
    workflowId = this.workflowId,
    createdAt = this.createdAt!!,
  )
