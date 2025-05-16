package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.plugins.requestvalidation.ValidationResult
import javax.activation.DataSource
import javax.mail.util.ByteArrayDataSource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.EmailAttachment
import net.barrage.llmao.core.SchemaValidation
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.ValidationError
import net.barrage.llmao.core.addSchemaErr
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

@Serializable
data class BonvoyageTravelManagerInsert(
  val userId: String,
  val userFullName: String,
  val userEmail: String,
)

@Serializable
data class BonvoyageTravelManagerUserMappingInsert(
  val travelManagerId: String,
  val userId: String,
  val delivery: BonvoyageNotificationDelivery,
)

@Serializable
data class BonvoyageTravelExpenseInsert(
  val amount: Double,
  val currency: String,
  val description: String,
  val imagePath: String,
  val imageProvider: String?,
  val expenseCreatedAt: KOffsetDateTime,
)

@Serializable
data class BonvoyageTravelExpenseUpdate(
  val expenseId: KUUID,
  val properties: BonvoyageTravelExpenseUpdateProperties,
)

/** DTO for requesting a Bonvoyage workflow. */
@Serializable
@SchemaValidation("validateSchema")
data class TravelRequest(
  /**
   * The type of transport that determines additional parameters that must be provided upon
   * finalizing the trip.
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

  /** UTC start date and time of the trip. */
  val startDateTime: KOffsetDateTime,

  /** UTC end date and time of the trip. */
  val endDateTime: KOffsetDateTime,

  /** Description of the trip's purpose. */
  val description: String,

  // Optional fields for personal vehicle

  /** Vehicle brand and model. */
  val vehicleType: String? = null,

  /** Vehicle license plates. */
  val vehicleRegistration: String? = null,
) : Validation {
  fun validateSchema(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()

    val now = KOffsetDateTime.now()

    if (startDateTime > endDateTime) {
      errors.addSchemaErr(message = "Start date and time must be before end date and time")
    }

    if (startDateTime <= now) {
      errors.addSchemaErr(message = "Start date and time must be in the future")
    }

    if (endDateTime <= now) {
      errors.addSchemaErr(message = "End date and time must be in the future")
    }

    if (transportType == TransportType.PERSONAL) {
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

@Serializable
data class BonvoyageTravelRequestStatusUpdate(
  val id: KUUID,
  val status: BonvoyageTravelRequestStatus,
  val reviewComment: String? = null,
)

/** Trip parameters. */
data class BonvoyageTripInsert(
  /** User ID on the auth server. */
  val userId: String,

  /** User's full name. */
  val userFullName: String,

  /** User's email. */
  val userEmail: String,

  /** Travel order identifier used by accounting to link to the travel order. */
  val travelOrderId: String,

  /**
   * The type of transport on the trip. Determines additional parameters that must be provided upon
   * finalizing the trip, e.g. if a personal vehicle is used as a transport method.
   */
  val transportType: TransportType,

  /** Where the trip is starting. */
  val startLocation: String,

  /** Destination of the trip. */
  val stops: List<String>,

  /** Where the trip is ending. */
  val endLocation: String,

  /** The official start date and time of the trip. */
  val startDateTime: KOffsetDateTime,

  /** The official end date and time of the trip. */
  val endDateTime: KOffsetDateTime,

  /** The trip's description that should contain the purpose of the trip. */
  val description: String,

  // Optional fields for personal vehicle

  val vehicleType: String? = null,
  val vehicleRegistration: String? = null,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class BonvoyageTripUpdate : Validation {
  override fun validate(): ValidationResult {
    return super.validate()
  }
}

/** DTO for users starting a trip. */
@Serializable
@SerialName("start")
@SchemaValidation("validateSchema")
data class BonvoyageStartTrip(
  val actualStartDateTime: KOffsetDateTime,
  // Mandatory when personal vehicle, optional otherwise
  val startingMileage: String? = null,
  // Optional fields, this is the final chance to correct the vehicle details
  val vehicleType: PropertyUpdate<String> = PropertyUpdate.Undefined,
  val vehicleRegistration: PropertyUpdate<String> = PropertyUpdate.Undefined,
) : Validation, BonvoyageTripUpdate() {
  fun validateSchema(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()

    if (
      vehicleType != PropertyUpdate.Undefined && vehicleRegistration == PropertyUpdate.Undefined
    ) {
      errors.addSchemaErr(
        message = "`vehicleRegistration` is required when `vehicleType` is provided"
      )
    }

    if (
      vehicleType == PropertyUpdate.Undefined && vehicleRegistration != PropertyUpdate.Undefined
    ) {
      errors.addSchemaErr(
        message = "`vehicleType` is required when `vehicleRegistration` is provided"
      )
    }

    return errors
  }
}

/** DTO for users updating a trip's description. */
@Serializable
@SerialName("update")
data class BonvoyageTripPropertiesUpdate(
  val actualStartDateTime: PropertyUpdate<KOffsetDateTime> = PropertyUpdate.Undefined,
  val actualEndDateTime: PropertyUpdate<KOffsetDateTime> = PropertyUpdate.Undefined,
  val startMileage: PropertyUpdate<String> = PropertyUpdate.Undefined,
  val endMileage: PropertyUpdate<String> = PropertyUpdate.Undefined,
  val description: String? = null,
) : BonvoyageTripUpdate()

/** DTO for users ending a trip. */
@Serializable
@SerialName("end")
data class BonvoyageEndTrip(
  val actualEndDateTime: KOffsetDateTime,
  val endMileage: String? = null,
) : BonvoyageTripUpdate()

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

/** Used to send PDF attachments in emails. */
data class BonvoyageTripReport(val bytes: ByteArray, val travelOrderId: String) : EmailAttachment {
  override fun bytes(): DataSource = ByteArrayDataSource(bytes, "application/pdf")

  override fun name(): String = travelOrderId

  override fun description(): String = "Trip report for $travelOrderId"
}
