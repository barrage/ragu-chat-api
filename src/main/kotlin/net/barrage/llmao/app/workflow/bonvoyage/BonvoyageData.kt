package net.barrage.llmao.app.workflow.bonvoyage

import javax.activation.DataSource
import javax.mail.util.ByteArrayDataSource
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.EmailAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.types.KOffsetDateTime

/** DTO for starting a Tripotron workflow. */
@Serializable
data class StartTrip(
  /**
   * The type of transport that determines additional parameters that must be provided upon
   * finalizing the trip.
   */
  val transportType: TransportType,

  /** Where the trip is starting. */
  val startLocation: String,

  /** Trip destination. */
  val destination: String,

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

  /** The start mileage of the vehicle. */
  val startMileage: String? = null,
)

/** Trip parameters. */
data class TripDetails(
  /** The tripper. */
  val user: User,

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
  val destination: String,

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
  val startMileage: String? = null,
  val endMileage: String? = null,
)

@Serializable
enum class TransportType {
  /** The trip is performed with public transport. */
  Public,

  /**
   * The trip is performed with a personal vehicle. This mandates that start and end mileage be
   * provided along with the vehicle's registration plates.
   */
  Personal,
}


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

/**
 * Used to send PDF attachments in emails.
 */
data class BonvoyageTripReport(val bytes: ByteArray, val travelOrderId: String) : EmailAttachment {
  override fun bytes(): DataSource = ByteArrayDataSource(bytes, "application/pdf")

  override fun name(): String = travelOrderId

  override fun description(): String = "Trip report for $travelOrderId"
}
