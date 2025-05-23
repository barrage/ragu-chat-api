package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.EmailAttachment
import net.barrage.llmao.core.NotBlank
import net.barrage.llmao.core.SchemaValidation
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.ValidationError
import net.barrage.llmao.core.addSchemaErr
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.types.KLocalDate
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KOffsetTime
import net.barrage.llmao.types.KUUID
import javax.activation.DataSource
import javax.mail.util.ByteArrayDataSource

@Serializable
data class TravelManagerUserMappingInsert(
  @NotBlank val travelManagerId: String,
  @NotBlank val userId: String,
  val delivery: BonvoyageNotificationDelivery,
) : Validation

/**
 * Used by administrators to create multiple travel orders at once.
 *
 * In this payload, the isDriver field is ignored on the [params] and is instead determined by the
 * [driverId] field.
 */
@Serializable
@SchemaValidation("validateSchema")
data class BulkInsertTrip(
  val travelers: List<BonvoyageUser>,

  /** Only necessary if the transport type is [TransportType.PERSONAL]. */
  val driverId: String? = null,
  val params: TravelRequestParameters,
) : Validation {
  fun validateSchema(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()

    if (travelers.isEmpty()) {
      errors.addSchemaErr(message = "At least one traveler must be specified")
    }

    if (params.transportType == TransportType.PERSONAL && driverId == null) {
      errors.addSchemaErr(message = "Driver must be specified for trips with a personal vehicle")
    }

    if (driverId != null && travelers.none { it.userId == driverId }) {
      errors.addSchemaErr(message = "Driver must be one of the travelers")
    }

    return errors
  }
}

/** Used by admins to directly insert trips without travel requests. */
@Serializable
data class InsertTrip(val traveler: BonvoyageUser, val params: TravelRequestParameters) :
  Validation {
  fun toTripInsert(travelOrderId: String, creatingUser: BonvoyageUser) =
    TripInsert(
      traveler = traveler,
      travelOrderId = travelOrderId,
      params = params,
      creatingUser = creatingUser,
    )
}

/**
 * Optional expected times can be entered in case there is more information on the exact start/end
 * times. Can be used to set up reminders for the user on when their trip start or ends.
 */
@Serializable
data class ApproveTravelRequest(
  /** Travel request ID. */
  val requestId: KUUID,

  /** Travel manager user ID. */
  val reviewerId: String,

  /** Optional explanation. */
  val reviewerComment: String?,

  /** Overrides the expected start time of the request. */
  val expectedStartTime: KOffsetTime? = null,

  /** Overrides the expected end time of the request. */
  val expectedEndTime: KOffsetTime? = null,
)

/** A subset of trip parameters necessary for creating a trip. */
@Serializable
@SchemaValidation("validateSchema")
data class TravelRequestParameters(
  /**
   * The type of transport on the trip. Determines additional parameters that must be provided upon
   * finalizing the trip, e.g. if a personal vehicle is used as a transport method.
   */
  val transportType: TransportType,

  /** Where the trip is starting. */
  val startLocation: String,

  /**
   * Trip stops.
   *
   * See [BonvoyageTrip.stops].
   */
  val stops: List<String>,

  /** Where the trip is ending. */
  val endLocation: String,

  /** Start date of the trip. */
  val startDate: KLocalDate,

  /** End date of the trip. */
  val endDate: KLocalDate,

  /** Description of the trip's purpose. */
  @NotBlank val description: String,

  // Optional fields for reminders

  /** Anticipated start time of the trip, used for reminders. */
  val expectedStartTime: KOffsetTime? = null,

  /** Anticipated end time of the trip, used for reminders. */
  val expectedEndTime: KOffsetTime? = null,

  // Optional fields for personal vehicle

  /** Vehicle brand and model. Only necessary if the transport type is [TransportType.PERSONAL]. */
  @NotBlank val vehicleType: String? = null,

  /** Vehicle license plates. */
  @NotBlank val vehicleRegistration: String? = null,

  /**
   * A flag enabling additional assertions upon creating the travel request, and prompts when
   * starting the trip. Ignored if the transport type is not [TransportType.PERSONAL].
   */
  val isDriver: Boolean = false,
) : Validation {
  fun validateSchema(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()

    val now = KLocalDate.now()

    if (stops.any { it.isBlank() }) {
      errors.addSchemaErr(message = "Stops cannot contain empty strings")
    }

    if (startDate > endDate) {
      errors.addSchemaErr(message = "Start date must be before end date")
    }

    if (startDate < now) {
      errors.addSchemaErr(message = "Start date must be today or in the future")
    }

    if (endDate < now) {
      errors.addSchemaErr(message = "End date must be today or in the future")
    }

    if (transportType == TransportType.PERSONAL && isDriver) {
      if (vehicleType == null) {
        errors.addSchemaErr(message = "`vehicleType` is required for trips with a personal vehicle")
      }
      if (vehicleRegistration == null) {
        errors.addSchemaErr(
          message = "`vehicleRegistration` is required for trips with a personal vehicle"
        )
      }
    }

    return errors
  }
}

/**
 * Optional expected times can be entered in case there is more information on the exact start/end
 * times. Can be used to set up reminders for the user on when their trip start or ends. These are
 * ignored if the request is rejected.
 */
@Serializable
data class TravelRequestStatusUpdate(
  val id: KUUID,
  val status: BonvoyageTravelRequestStatus,
  val reviewComment: String? = null,
  val expectedStartTime: KOffsetTime? = null,
  val expectedEndTime: KOffsetTime? = null,
)

/** Trip parameters. */
@Serializable
data class TripInsert(
  val traveler: BonvoyageUser,

  /**
   * Travel order identifier used by accounting to link to the travel order.
   *
   * If provided during admin trip creation, it will be used. Otherwise, a new one will be generated
   * using the downstream travel order provider.
   */
  val travelOrderId: String,

  /** We only need a subset of actual trip parameters when creating it. */
  val params: TravelRequestParameters,

  /** User who is inserting the trip directly or by approving a travel request. */
  val creatingUser: BonvoyageUser,
) : Validation

@Serializable
@SerialName("reminders")
data class TripUpdateReminders(
  val expectedStartTime: PropertyUpdate<KOffsetTime> = PropertyUpdate.Undefined,
  val expectedEndTime: PropertyUpdate<KOffsetTime> = PropertyUpdate.Undefined,
)

/** Structure for updating any of the trip's properties. */
@Serializable
data class TripPropertiesUpdate(
  /** Once a start time has been set, it cannot be changed to null */
  val startTime: KOffsetTime? = null,

  /** The end date can also be modified by the user, in case of unforeseen circumstances. */
  val endDate: KLocalDate? = null,

  /** Once the end time has been set, it cannot be changed to null. */
  val endTime: KOffsetTime? = null,

  /** Required if the transport type is [TransportType.PERSONAL] and the user is the driver. */
  @NotBlank val startMileage: PropertyUpdate<String> = PropertyUpdate.Undefined,

  /** Required if the transport type is [TransportType.PERSONAL] and the user is the driver. */
  @NotBlank val endMileage: PropertyUpdate<String> = PropertyUpdate.Undefined,

  /** Updates the vehicle type, in case of changes in the trip. */
  @NotBlank val vehicleType: PropertyUpdate<String> = PropertyUpdate.Undefined,

  /** Updates the vehicle registration, in case of changes in the trip. */
  @NotBlank val vehicleRegistration: PropertyUpdate<String> = PropertyUpdate.Undefined,

  /**
   * Updates the trip's description. Clients should prefill this to current and send in case of
   * updates.
   */
  @NotBlank val description: String? = null,
) : Validation

/**
 * Data obtained from the LLM when parsing receipts. Ultimately turned into a
 * [BonvoyageTravelExpense].
 */
@Serializable
data class TravelExpense(
  val amount: Double,
  val currency: String,
  val description: String,
  val createdAt: KOffsetDateTime,
)

@Serializable
data class TravelExpenseInsert(
  val amount: Double,
  val currency: String,
  val description: String,
  val imagePath: String,
  val imageProvider: String?,
  val expenseCreatedAt: KOffsetDateTime,
)

/** Used to send PDF attachments in emails. */
data class BonvoyageTripReport(val bytes: ByteArray, val travelOrderId: String) : EmailAttachment {
  override fun bytes(): DataSource = ByteArrayDataSource(bytes, "application/pdf")

  override fun name(): String = travelOrderId

  override fun description(): String = "Trip report for $travelOrderId"
}
