package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.model.User
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

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

@Serializable
data class TravelExpense(
  /** * Expense identifier. */
  val id: KUUID,

  /** The amount of the expense in the currency specified. */
  val amount: Double,

  /** Expense currency. */
  val currency: String,

  /** Expense description. */
  val description: String,

  /** Path to the image associated with the expense. */
  val imagePath: String,

  /** Storage provider of the expense image. */
  val imageProvider: String,

  /**
   * The date-time of the expense as indicated on the image. Not to be confused with the creation
   * date-time of the actual expense entry.
   */
  val createdAt: KOffsetDateTime,
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
